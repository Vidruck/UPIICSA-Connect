use crate::transport::{
    FrameHeader, TransportMultiplexer, UpiicsaFrame, WireFrameError, FRAME_HEADER_LEN,
    FRAME_SIGNATURE,
};

/// Estadísticas acumuladas del decodificador de flujo para telemetría de calidad del enlace.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct StreamDecoderStats {
    pub frames_ok: u64,
    pub frames_rejected: u64,
    pub bytes_discarded: u64,
}

/// Reensambla tramas `UpiicsaFrame` a partir de lecturas parciales de un socket o endpoint USB y se resincroniza ante basura.
#[derive(Debug, Default)]
pub struct FrameStreamDecoder {
    buffer: Vec<u8>,
    stats: StreamDecoderStats,
}

impl FrameStreamDecoder {
    /// Crea un decodificador vacío.
    pub fn new() -> Self {
        Self::default()
    }

    /// Agrega bytes recién leídos del transporte al búfer interno.
    pub fn feed(&mut self, chunk: &[u8]) {
        self.buffer.extend_from_slice(chunk);
    }

    /// Cantidad de bytes pendientes en el búfer que aún no forman una trama completa.
    pub fn pending_bytes(&self) -> usize {
        self.buffer.len()
    }

    /// Devuelve las estadísticas acumuladas desde la creación del decodificador.
    pub fn stats(&self) -> StreamDecoderStats {
        self.stats
    }

    /// Extrae la siguiente trama disponible: `None` si faltan bytes, `Some(Err)` si se descartó una trama inválida.
    pub fn next_frame(&mut self) -> Option<Result<UpiicsaFrame, WireFrameError>> {
        self.discard_until_signature();
        if self.buffer.len() < FRAME_HEADER_LEN {
            return None;
        }
        let header = match FrameHeader::parse(&self.buffer) {
            Ok(header) => header,
            Err(error) => {
                // [DECISIÓN TÉCNICA]: Ante cabecera inválida se descarta solo la firma para buscar la siguiente coincidencia "UC".
                self.discard(FRAME_SIGNATURE.len());
                self.stats.frames_rejected += 1;
                return Some(Err(error));
            }
        };
        if self.buffer.len() < header.total_len() {
            return None;
        }
        let result = TransportMultiplexer::decode_wire_bytes(&self.buffer[..header.total_len()]);
        self.buffer.drain(..header.total_len());
        match result {
            Ok(frame) => {
                self.stats.frames_ok += 1;
                Some(Ok(frame))
            }
            Err(error) => {
                self.stats.frames_rejected += 1;
                self.stats.bytes_discarded += header.total_len() as u64;
                Some(Err(error))
            }
        }
    }

    /// Extrae todas las tramas válidas disponibles ignorando las rechazadas (ya contabilizadas en estadísticas).
    pub fn drain_valid_frames(&mut self) -> Vec<UpiicsaFrame> {
        let mut frames = Vec::new();
        while let Some(result) = self.next_frame() {
            if let Ok(frame) = result {
                frames.push(frame);
            }
        }
        frames
    }

    /// Elimina bytes iniciales hasta encontrar la firma de protocolo (conserva una posible 'U' final incompleta).
    fn discard_until_signature(&mut self) {
        let position = self
            .buffer
            .windows(FRAME_SIGNATURE.len())
            .position(|window| window == FRAME_SIGNATURE);
        match position {
            Some(0) => {}
            Some(index) => self.discard(index),
            None => {
                let keep = usize::from(self.buffer.last() == Some(&FRAME_SIGNATURE[0]));
                let drop = self.buffer.len() - keep;
                self.discard(drop);
            }
        }
    }

    /// Descarta `count` bytes del inicio del búfer registrándolos en estadísticas.
    fn discard(&mut self, count: usize) {
        let count = count.min(self.buffer.len());
        self.buffer.drain(..count);
        self.stats.bytes_discarded += count as u64;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::transport::{build_frame, ServiceChannel, TransportMedium};

    /// Construye bytes serializados de una trama de prueba.
    fn trama(seq: u32, payload: &[u8]) -> Vec<u8> {
        TransportMultiplexer::encode_wire_bytes(&build_frame(
            TransportMedium::UsbCable,
            ServiceChannel::ClipboardSync,
            seq,
            payload.to_vec(),
        ))
    }

    /// Verifica el reensamblado de dos tramas entregadas byte por byte.
    #[test]
    fn prueba_reensamblado_byte_a_byte() {
        let mut flujo = trama(1, b"hola");
        flujo.extend(trama(2, b"UPIICSA"));
        let mut decoder = FrameStreamDecoder::new();
        let mut recibidas = Vec::new();
        for byte in flujo {
            decoder.feed(&[byte]);
            recibidas.extend(decoder.drain_valid_frames());
        }
        assert_eq!(recibidas.len(), 2);
        assert_eq!(recibidas[1].payload, b"UPIICSA");
        assert_eq!(decoder.pending_bytes(), 0);
    }

    /// Verifica la resincronización cuando hay basura antes y entre tramas válidas.
    #[test]
    fn prueba_resincronizacion_con_basura() {
        let mut flujo = vec![0x00, 0x13, b'U', 0x37];
        flujo.extend(trama(5, b"a"));
        flujo.extend([b'U', b'C', 0x09, 0x09]);
        flujo.extend(trama(6, b"b"));
        let mut decoder = FrameStreamDecoder::new();
        decoder.feed(&flujo);
        let frames = decoder.drain_valid_frames();
        assert_eq!(frames.len(), 2);
        assert_eq!(frames[0].sequence_id, 5);
        assert_eq!(frames[1].sequence_id, 6);
        assert_eq!(decoder.stats().frames_ok, 2);
        assert!(decoder.stats().frames_rejected >= 1);
    }

    /// Verifica que una trama corrupta se descarte completa sin afectar a la siguiente.
    #[test]
    fn prueba_trama_corrupta_descartada() {
        let mut corrupta = trama(1, b"dato");
        let ultimo = corrupta.len() - 1;
        corrupta[ultimo] ^= 0x01;
        corrupta.extend(trama(2, b"sano"));
        let mut decoder = FrameStreamDecoder::new();
        decoder.feed(&corrupta);
        assert_eq!(
            decoder.next_frame(),
            Some(Err(WireFrameError::ChecksumMismatch))
        );
        let siguiente = decoder.next_frame().unwrap().unwrap();
        assert_eq!(siguiente.payload, b"sano");
        assert_eq!(decoder.next_frame(), None);
    }
}
