/// Medios físicos de transporte soportados por UPIICSA-Connect en orden de prioridad.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportMedium {
    UsbCable = 1,
    WifiLan = 2,
    BluetoothRfcomm = 3,
}

/// Canales lógicos multiplexados dentro de una misma sesión PC-Android.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ServiceChannel {
    NotificationResolution = 1,
    ClipboardSync = 2,
    SecondaryDisplayVideo = 3,
    WebcamMicStream = 4,
    MusicModeAudio = 5,
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
            preferred_order: [
                TransportMedium::UsbCable,
                TransportMedium::WifiLan,
                TransportMedium::BluetoothRfcomm,
            ],
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
        let crc32 = compute_crc32(&payload, channel as u8, sequence_id);
        UpiicsaFrame {
            medium,
            channel,
            sequence_id,
            payload,
            crc32,
        }
    }

    /// Serializa una trama en un vector de bytes listo para enviarse por USB, Wi-Fi o Bluetooth.
    pub fn encode_wire_bytes(frame: &UpiicsaFrame) -> Vec<u8> {
        let payload_len = frame.payload.len() as u32;
        let mut buffer = Vec::with_capacity(16 + frame.payload.len());
        // [DECISIÓN TÉCNICA]: Cabecera fija de 16 bytes ("UC" + medio + canal + secuencia + longitud + CRC32) sin depender de serde.
        buffer.extend_from_slice(b"UC");
        buffer.push(frame.medium as u8);
        buffer.push(frame.channel as u8);
        buffer.extend_from_slice(&frame.sequence_id.to_be_bytes());
        buffer.extend_from_slice(&payload_len.to_be_bytes());
        buffer.extend_from_slice(&frame.crc32.to_be_bytes());
        buffer.extend_from_slice(&frame.payload);
        buffer
    }

    /// Decodifica bytes recibidos del transporte y valida la firma y el CRC32 antes de entregarlos al dominio.
    pub fn decode_wire_bytes(bytes: &[u8]) -> Result<UpiicsaFrame, &'static str> {
        if bytes.len() < 16 {
            return Err("Trama incompleta: cabecera menor a 16 bytes");
        }
        if &bytes[0..2] != b"UC" {
            return Err("Firma de protocolo invalida");
        }
        let medium = match bytes[2] {
            1 => TransportMedium::UsbCable,
            2 => TransportMedium::WifiLan,
            3 => TransportMedium::BluetoothRfcomm,
            _ => return Err("Medio de transporte desconocido"),
        };
        let channel = match bytes[3] {
            1 => ServiceChannel::NotificationResolution,
            2 => ServiceChannel::ClipboardSync,
            3 => ServiceChannel::SecondaryDisplayVideo,
            4 => ServiceChannel::WebcamMicStream,
            5 => ServiceChannel::MusicModeAudio,
            _ => return Err("Canal de servicio desconocido"),
        };
        let sequence_id = u32::from_be_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]);
        let payload_len = u32::from_be_bytes([bytes[8], bytes[9], bytes[10], bytes[11]]) as usize;
        let expected_crc = u32::from_be_bytes([bytes[12], bytes[13], bytes[14], bytes[15]]);
        if bytes.len() < 16 + payload_len {
            return Err("Longitud de carga util inconsistente");
        }
        let payload = bytes[16..16 + payload_len].to_vec();
        let actual_crc = compute_crc32(&payload, channel as u8, sequence_id);
        if actual_crc != expected_crc {
            return Err("Error de integridad CRC32 en trama");
        }
        Ok(UpiicsaFrame {
            medium,
            channel,
            sequence_id,
            payload,
            crc32: expected_crc,
        })
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
