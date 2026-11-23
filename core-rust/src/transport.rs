/// Longitud fija en bytes de la cabecera de una trama `UpiicsaFrame` serializada.
pub const FRAME_HEADER_LEN: usize = 16;

/// Tamaño máximo aceptado para la carga útil de una trama (4 MiB) para evitar reservas de memoria abusivas.
pub const MAX_PAYLOAD_BYTES: usize = 4 * 1024 * 1024;

/// Firma de protocolo con la que inicia toda trama serializada.
pub const FRAME_SIGNATURE: [u8; 2] = *b"UC";

/// Medios físicos de transporte soportados por UPIICSA-Connect en orden de prioridad.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum TransportMedium {
    UsbCable = 1,
    WifiLan = 2,
    BluetoothRfcomm = 3,
}

impl TransportMedium {
    /// Convierte el identificador numérico del protocolo en un medio de transporte conocido.
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::UsbCable),
            2 => Some(Self::WifiLan),
            3 => Some(Self::BluetoothRfcomm),
            _ => None,
        }
    }

    /// Devuelve todos los medios ordenados de mayor a menor prioridad (USB -> Wi-Fi -> Bluetooth).
    pub fn by_priority() -> [Self; 3] {
        [Self::UsbCable, Self::WifiLan, Self::BluetoothRfcomm]
    }
}

/// Canales lógicos multiplexados dentro de una misma sesión PC-Android.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ServiceChannel {
    NotificationResolution = 1,
    ClipboardSync = 2,
    SecondaryDisplayVideo = 3,
    WebcamMicStream = 4,
    MusicModeAudio = 5,
    SessionControl = 6,
}

impl ServiceChannel {
    /// Convierte el identificador numérico del protocolo en un canal lógico conocido.
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::NotificationResolution),
            2 => Some(Self::ClipboardSync),
            3 => Some(Self::SecondaryDisplayVideo),
            4 => Some(Self::WebcamMicStream),
            5 => Some(Self::MusicModeAudio),
            6 => Some(Self::SessionControl),
            _ => None,
        }
    }
}

/// Errores posibles al validar o decodificar una trama recibida del transporte.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WireFrameError {
    IncompleteHeader,
    InvalidSignature,
    UnknownMedium,
    UnknownChannel,
    PayloadTooLarge,
    InconsistentLength,
    ChecksumMismatch,
}

impl WireFrameError {
    /// Código numérico negativo estable expuesto por la interfaz FFI hacia Kotlin.
    pub fn code(self) -> i32 {
        match self {
            Self::IncompleteHeader => -1,
            Self::InvalidSignature => -2,
            Self::UnknownMedium => -3,
            Self::UnknownChannel => -4,
            Self::PayloadTooLarge => -5,
            Self::InconsistentLength => -6,
            Self::ChecksumMismatch => -7,
        }
    }

    /// Descripción en español del error para bitácoras y telemetría.
    pub fn message(self) -> &'static str {
        match self {
            Self::IncompleteHeader => "Trama incompleta: cabecera menor a 16 bytes",
            Self::InvalidSignature => "Firma de protocolo invalida",
            Self::UnknownMedium => "Medio de transporte desconocido",
            Self::UnknownChannel => "Canal de servicio desconocido",
            Self::PayloadTooLarge => "Carga util supera el maximo permitido",
            Self::InconsistentLength => "Longitud de carga util inconsistente",
            Self::ChecksumMismatch => "Error de integridad CRC32 en trama",
        }
    }
}

/// Cabecera decodificada de una trama, útil para validar antes de copiar la carga útil.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct FrameHeader {
    pub medium: TransportMedium,
    pub channel: ServiceChannel,
    pub sequence_id: u32,
    pub payload_len: usize,
    pub crc32: u32,
}

impl FrameHeader {
    /// Interpreta los primeros 16 bytes de una trama validando firma, medio, canal y límite de tamaño.
    pub fn parse(bytes: &[u8]) -> Result<Self, WireFrameError> {
        if bytes.len() < FRAME_HEADER_LEN {
            return Err(WireFrameError::IncompleteHeader);
        }
        if bytes[0..2] != FRAME_SIGNATURE {
            return Err(WireFrameError::InvalidSignature);
        }
        let medium = TransportMedium::from_u8(bytes[2]).ok_or(WireFrameError::UnknownMedium)?;
        let channel = ServiceChannel::from_u8(bytes[3]).ok_or(WireFrameError::UnknownChannel)?;
        let sequence_id = u32::from_be_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]);
        let payload_len = u32::from_be_bytes([bytes[8], bytes[9], bytes[10], bytes[11]]) as usize;
        if payload_len > MAX_PAYLOAD_BYTES {
            return Err(WireFrameError::PayloadTooLarge);
        }
        let crc32 = u32::from_be_bytes([bytes[12], bytes[13], bytes[14], bytes[15]]);
        Ok(Self {
            medium,
            channel,
            sequence_id,
            payload_len,
            crc32,
        })
    }

    /// Longitud total en bytes (cabecera + carga útil) que ocupa la trama en el flujo.
    pub fn total_len(&self) -> usize {
        FRAME_HEADER_LEN + self.payload_len
    }
}

/// Trama binaria con cabecera determinista y verificación de integridad CRC32.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UpiicsaFrame {
    pub medium: TransportMedium,
    pub channel: ServiceChannel,
    pub sequence_id: u32,
    pub payload: Vec<u8>,
    pub crc32: u32,
}

/// Selector y multiplexor de transporte que garantiza prioridad de cable USB sobre redes inalámbricas.
#[derive(Debug, Clone)]
pub struct TransportMultiplexer {
    preferred_order: [TransportMedium; 3],
    next_sequence: u32,
}

impl Default for TransportMultiplexer {
    /// Construye una instancia del multiplexor con la prioridad oficial USB -> Wi-Fi -> Bluetooth.
    fn default() -> Self {
        Self::new()
    }
}

impl TransportMultiplexer {
    /// Inicializa el multiplexor estableciendo el cable USB como primera opción y Wi-Fi/Bluetooth como segunda opción.
    pub fn new() -> Self {
        Self {
            preferred_order: TransportMedium::by_priority(),
            next_sequence: 1,
        }
    }

    /// Selecciona el mejor medio de transporte disponible respetando la prioridad estricta del proyecto.
    pub fn select_best_medium(
        &self,
        usb_available: bool,
        wifi_available: bool,
        bluetooth_available: bool,
    ) -> Option<TransportMedium> {
        for medium in self.preferred_order {
            let disponible = match medium {
                TransportMedium::UsbCable => usb_available,
                TransportMedium::WifiLan => wifi_available,
                TransportMedium::BluetoothRfcomm => bluetooth_available,
            };
            if disponible {
                return Some(medium);
            }
        }
        None
    }

    /// Empaqueta un bloque de datos en una trama etiquetada con canal lógico, secuencia y suma CRC32.
    pub fn pack_frame(
        &mut self,
        medium: TransportMedium,
        channel: ServiceChannel,
        payload: Vec<u8>,
    ) -> UpiicsaFrame {
        let sequence_id = self.next_sequence;
        self.next_sequence = self.next_sequence.wrapping_add(1);
        build_frame(medium, channel, sequence_id, payload)
    }

    /// Serializa una trama en un vector de bytes listo para enviarse por USB, Wi-Fi o Bluetooth.
    pub fn encode_wire_bytes(frame: &UpiicsaFrame) -> Vec<u8> {
        let payload_len = frame.payload.len() as u32;
        let mut buffer = Vec::with_capacity(FRAME_HEADER_LEN + frame.payload.len());
        // [DECISIÓN TÉCNICA]: Cabecera fija de 16 bytes ("UC" + medio + canal + secuencia + longitud + CRC32) sin depender de serde.
        buffer.extend_from_slice(&FRAME_SIGNATURE);
        buffer.push(frame.medium as u8);
        buffer.push(frame.channel as u8);
        buffer.extend_from_slice(&frame.sequence_id.to_be_bytes());
        buffer.extend_from_slice(&payload_len.to_be_bytes());
        buffer.extend_from_slice(&frame.crc32.to_be_bytes());
        buffer.extend_from_slice(&frame.payload);
        buffer
    }

    /// Decodifica bytes recibidos del transporte y valida firma, tamaño máximo y CRC32 antes de entregarlos al dominio.
    pub fn decode_wire_bytes(bytes: &[u8]) -> Result<UpiicsaFrame, WireFrameError> {
        let header = FrameHeader::parse(bytes)?;
        if bytes.len() < header.total_len() {
            return Err(WireFrameError::InconsistentLength);
        }
        let payload = bytes[FRAME_HEADER_LEN..header.total_len()].to_vec();
        let actual_crc = compute_crc32(&payload, header.channel as u8, header.sequence_id);
        if actual_crc != header.crc32 {
            return Err(WireFrameError::ChecksumMismatch);
        }
        Ok(UpiicsaFrame {
            medium: header.medium,
            channel: header.channel,
            sequence_id: header.sequence_id,
            payload,
            crc32: header.crc32,
        })
    }
}

/// Construye una trama con número de secuencia explícito calculando su CRC32.
pub fn build_frame(
    medium: TransportMedium,
    channel: ServiceChannel,
    sequence_id: u32,
    payload: Vec<u8>,
) -> UpiicsaFrame {
    let crc32 = compute_crc32(&payload, channel as u8, sequence_id);
    UpiicsaFrame {
        medium,
        channel,
        sequence_id,
        payload,
        crc32,
    }
}

/// Calcula un checksum CRC32 estándar IEEE 802.3 incluyendo canal y número de secuencia para evitar corrupción silenciosa.
pub fn compute_crc32(payload: &[u8], channel_tag: u8, sequence_id: u32) -> u32 {
    let mut crc: u32 = 0xFFFF_FFFF;
    let seq_bytes = sequence_id.to_be_bytes();
    for byte in [channel_tag]
        .iter()
        .chain(seq_bytes.iter())
        .chain(payload.iter())
    {
        crc ^= u32::from(*byte);
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0xEDB8_8320 & mask);
        }
    }
    !crc
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Verifica que la cabecera rechace cargas útiles mayores al máximo permitido sin reservar memoria.
    #[test]
    fn prueba_rechazo_de_carga_util_excesiva() {
        let mut bytes = vec![b'U', b'C', 1, 2, 0, 0, 0, 1];
        bytes.extend_from_slice(&((MAX_PAYLOAD_BYTES as u32) + 1).to_be_bytes());
        bytes.extend_from_slice(&[0, 0, 0, 0]);
        assert_eq!(
            TransportMultiplexer::decode_wire_bytes(&bytes),
            Err(WireFrameError::PayloadTooLarge)
        );
    }

    /// Verifica que un byte alterado en la carga útil sea detectado por el CRC32.
    #[test]
    fn prueba_deteccion_de_corrupcion() {
        let frame = build_frame(
            TransportMedium::WifiLan,
            ServiceChannel::ClipboardSync,
            9,
            b"texto".to_vec(),
        );
        let mut bytes = TransportMultiplexer::encode_wire_bytes(&frame);
        let last = bytes.len() - 1;
        bytes[last] ^= 0xFF;
        assert_eq!(
            TransportMultiplexer::decode_wire_bytes(&bytes),
            Err(WireFrameError::ChecksumMismatch)
        );
    }

    /// Comprueba la conversión de identificadores numéricos y el nuevo canal de control de sesión.
    #[test]
    fn prueba_conversion_de_identificadores() {
        assert_eq!(
            TransportMedium::from_u8(3),
            Some(TransportMedium::BluetoothRfcomm)
        );
        assert_eq!(TransportMedium::from_u8(9), None);
        assert_eq!(
            ServiceChannel::from_u8(6),
            Some(ServiceChannel::SessionControl)
        );
        assert_eq!(ServiceChannel::from_u8(0), None);
        assert_eq!(WireFrameError::ChecksumMismatch.code(), -7);
    }
}
