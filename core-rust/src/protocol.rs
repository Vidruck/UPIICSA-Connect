use crate::transport::ServiceChannel;

/// Longitud máxima en bytes de un texto codificado dentro de un mensaje (prefijo u16).
pub const MAX_TEXT_BYTES: usize = u16::MAX as usize;

/// Versión del formato de mensajes; viaja en `HandshakeHello` y ambos extremos deben coincidir para continuar.
pub const PROTOCOL_VERSION: u16 = 1;

/// Origen de un contenido sincronizado entre el ordenador y el teléfono.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ContentOrigin {
    Desktop = 1,
    Mobile = 2,
}

/// Dirección en la que se inició la solicitud de enlace.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LinkDirection {
    DesktopToMobile = 1,
    MobileToDesktop = 2,
}

/// Mensajes de aplicación tipados que viajan dentro de la carga útil de una `UpiicsaFrame`.
#[derive(Debug, Clone, PartialEq)]
pub enum ChannelMessage {
    NotificationReply {
        notification_id: String,
        reply_text: String,
    },
    NotificationMarkRead {
        notification_id: String,
    },
    NotificationDismiss {
        notification_id: String,
    },
    NotificationPosted {
        notification_id: String,
        app_name: String,
        package_name: String,
        sender_title: String,
        message_body: String,
        supports_inline_reply: bool,
    },
    ClipboardText {
        origin: ContentOrigin,
        text: String,
    },
    CameraMicCommand {
        camera_enabled: bool,
        microphone_enabled: bool,
        use_back_camera: bool,
    },
    MusicModeCommand {
        enabled: bool,
        gain_milli: u16,
    },
    HandshakeHello {
        direction: LinkDirection,
        device_name: String,
        pairing_pin: u32,
        protocol_version: u16,
    },
    Heartbeat {
        timestamp_ms: u64,
    },
}

/// Errores de codificación o decodificación de mensajes de aplicación.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProtocolError {
    EmptyPayload,
    UnknownTag,
    Truncated,
    TextTooLong,
    InvalidUtf8,
    InvalidEnumValue,
    TrailingBytes,
}

/// Etiquetas binarias de cada tipo de mensaje (primer byte de la carga útil).
mod tag {
    pub const NOTIFICATION_REPLY: u8 = 0x10;
    pub const NOTIFICATION_MARK_READ: u8 = 0x11;
    pub const NOTIFICATION_DISMISS: u8 = 0x12;
    pub const NOTIFICATION_POSTED: u8 = 0x13;
    pub const CLIPBOARD_TEXT: u8 = 0x20;
    pub const CAMERA_MIC_COMMAND: u8 = 0x30;
    pub const MUSIC_MODE_COMMAND: u8 = 0x40;
    pub const HANDSHAKE_HELLO: u8 = 0x50;
    pub const HEARTBEAT: u8 = 0x60;
}

impl ChannelMessage {
    /// Indica el canal lógico por el que debe viajar el mensaje.
    pub fn channel(&self) -> ServiceChannel {
        match self {
            Self::NotificationReply { .. }
            | Self::NotificationMarkRead { .. }
            | Self::NotificationDismiss { .. }
            | Self::NotificationPosted { .. } => ServiceChannel::NotificationResolution,
            Self::ClipboardText { .. } => ServiceChannel::ClipboardSync,
            Self::CameraMicCommand { .. } => ServiceChannel::WebcamMicStream,
            Self::MusicModeCommand { .. } => ServiceChannel::MusicModeAudio,
            Self::HandshakeHello { .. } | Self::Heartbeat { .. } => ServiceChannel::SessionControl,
        }
    }

    /// Codifica el mensaje en formato binario etiquetado con textos prefijados por longitud u16 big-endian.
    pub fn encode(&self) -> Result<Vec<u8>, ProtocolError> {
        let mut out = Vec::with_capacity(32);
        match self {
            Self::NotificationReply {
                notification_id,
                reply_text,
            } => {
                out.push(tag::NOTIFICATION_REPLY);
                write_text(&mut out, notification_id)?;
                write_text(&mut out, reply_text)?;
            }
            Self::NotificationMarkRead { notification_id } => {
                out.push(tag::NOTIFICATION_MARK_READ);
                write_text(&mut out, notification_id)?;
            }
            Self::NotificationDismiss { notification_id } => {
                out.push(tag::NOTIFICATION_DISMISS);
                write_text(&mut out, notification_id)?;
            }
            Self::NotificationPosted {
                notification_id,
                app_name,
                package_name,
                sender_title,
                message_body,
                supports_inline_reply,
            } => {
                out.push(tag::NOTIFICATION_POSTED);
                write_text(&mut out, notification_id)?;
                write_text(&mut out, app_name)?;
                write_text(&mut out, package_name)?;
                write_text(&mut out, sender_title)?;
                write_text(&mut out, message_body)?;
                out.push(u8::from(*supports_inline_reply));
            }
            Self::ClipboardText { origin, text } => {
                out.push(tag::CLIPBOARD_TEXT);
                out.push(*origin as u8);
                write_text(&mut out, text)?;
            }
            Self::CameraMicCommand {
                camera_enabled,
                microphone_enabled,
                use_back_camera,
            } => {
                out.push(tag::CAMERA_MIC_COMMAND);
                out.push(u8::from(*camera_enabled));
                out.push(u8::from(*microphone_enabled));
                out.push(u8::from(*use_back_camera));
            }
            Self::MusicModeCommand {
                enabled,
                gain_milli,
            } => {
                out.push(tag::MUSIC_MODE_COMMAND);
                out.push(u8::from(*enabled));
                out.extend_from_slice(&gain_milli.to_be_bytes());
            }
            Self::HandshakeHello {
                direction,
                device_name,
                pairing_pin,
                protocol_version,
            } => {
                out.push(tag::HANDSHAKE_HELLO);
                out.push(*direction as u8);
                write_text(&mut out, device_name)?;
                out.extend_from_slice(&pairing_pin.to_be_bytes());
                out.extend_from_slice(&protocol_version.to_be_bytes());
            }
            Self::Heartbeat { timestamp_ms } => {
                out.push(tag::HEARTBEAT);
                out.extend_from_slice(&timestamp_ms.to_be_bytes());
            }
        }
        Ok(out)
    }

    /// Decodifica una carga útil binaria validando etiqueta, longitudes, UTF-8 y ausencia de bytes sobrantes.
    pub fn decode(bytes: &[u8]) -> Result<Self, ProtocolError> {
        let (&first, rest) = bytes.split_first().ok_or(ProtocolError::EmptyPayload)?;
        let mut reader = ByteReader::new(rest);
        let message = match first {
            tag::NOTIFICATION_REPLY => Self::NotificationReply {
                notification_id: reader.text()?,
                reply_text: reader.text()?,
            },
            tag::NOTIFICATION_MARK_READ => Self::NotificationMarkRead {
                notification_id: reader.text()?,
            },
            tag::NOTIFICATION_DISMISS => Self::NotificationDismiss {
                notification_id: reader.text()?,
            },
            tag::NOTIFICATION_POSTED => Self::NotificationPosted {
                notification_id: reader.text()?,
                app_name: reader.text()?,
                package_name: reader.text()?,
                sender_title: reader.text()?,
                message_body: reader.text()?,
                supports_inline_reply: reader.flag()?,
            },
            tag::CLIPBOARD_TEXT => {
                let origin = match reader.u8()? {
                    1 => ContentOrigin::Desktop,
                    2 => ContentOrigin::Mobile,
                    _ => return Err(ProtocolError::InvalidEnumValue),
                };
                Self::ClipboardText {
                    origin,
                    text: reader.text()?,
                }
            }
            tag::CAMERA_MIC_COMMAND => Self::CameraMicCommand {
                camera_enabled: reader.flag()?,
                microphone_enabled: reader.flag()?,
                use_back_camera: reader.flag()?,
            },
            tag::MUSIC_MODE_COMMAND => Self::MusicModeCommand {
                enabled: reader.flag()?,
                gain_milli: reader.u16()?,
            },
            tag::HANDSHAKE_HELLO => {
                let direction = match reader.u8()? {
                    1 => LinkDirection::DesktopToMobile,
                    2 => LinkDirection::MobileToDesktop,
                    _ => return Err(ProtocolError::InvalidEnumValue),
                };
                Self::HandshakeHello {
                    direction,
                    device_name: reader.text()?,
                    pairing_pin: reader.u32()?,
                    protocol_version: reader.u16()?,
                }
            }
            tag::HEARTBEAT => Self::Heartbeat {
                timestamp_ms: reader.u64()?,
            },
            _ => return Err(ProtocolError::UnknownTag),
        };
        reader.finish()?;
        Ok(message)
    }
}

/// Escribe un texto UTF-8 precedido por su longitud en u16 big-endian.
fn write_text(out: &mut Vec<u8>, text: &str) -> Result<(), ProtocolError> {
    let bytes = text.as_bytes();
    if bytes.len() > MAX_TEXT_BYTES {
        return Err(ProtocolError::TextTooLong);
    }
    out.extend_from_slice(&(bytes.len() as u16).to_be_bytes());
    out.extend_from_slice(bytes);
    Ok(())
}

/// Lector secuencial de bytes con verificación de límites para decodificar mensajes.
struct ByteReader<'a> {
    bytes: &'a [u8],
    position: usize,
}

impl<'a> ByteReader<'a> {
    /// Crea un lector posicionado al inicio del arreglo recibido.
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, position: 0 }
    }

    /// Toma `count` bytes avanzando la posición o reporta truncamiento.
    fn take(&mut self, count: usize) -> Result<&'a [u8], ProtocolError> {
        let end = self
            .position
            .checked_add(count)
            .ok_or(ProtocolError::Truncated)?;
        let slice = self
            .bytes
            .get(self.position..end)
            .ok_or(ProtocolError::Truncated)?;
        self.position = end;
        Ok(slice)
    }

    /// Lee un byte sin signo.
    fn u8(&mut self) -> Result<u8, ProtocolError> {
        Ok(self.take(1)?[0])
    }

    /// Lee un booleano codificado como 0 o 1.
    fn flag(&mut self) -> Result<bool, ProtocolError> {
        match self.u8()? {
            0 => Ok(false),
            1 => Ok(true),
            _ => Err(ProtocolError::InvalidEnumValue),
        }
    }

    /// Lee un entero u16 big-endian.
    fn u16(&mut self) -> Result<u16, ProtocolError> {
        let b = self.take(2)?;
        Ok(u16::from_be_bytes([b[0], b[1]]))
    }

    /// Lee un entero u32 big-endian.
    fn u32(&mut self) -> Result<u32, ProtocolError> {
        let b = self.take(4)?;
        Ok(u32::from_be_bytes([b[0], b[1], b[2], b[3]]))
    }

    /// Lee un entero u64 big-endian.
    fn u64(&mut self) -> Result<u64, ProtocolError> {
        let b = self.take(8)?;
        let mut raw = [0_u8; 8];
        raw.copy_from_slice(b);
        Ok(u64::from_be_bytes(raw))
    }

    /// Lee un texto UTF-8 prefijado por longitud u16.
    fn text(&mut self) -> Result<String, ProtocolError> {
        let len = self.u16()? as usize;
        let raw = self.take(len)?;
        String::from_utf8(raw.to_vec()).map_err(|_| ProtocolError::InvalidUtf8)
    }

    /// Verifica que no queden bytes sin consumir al terminar la decodificación.
    fn finish(&self) -> Result<(), ProtocolError> {
        if self.position == self.bytes.len() {
            Ok(())
        } else {
            Err(ProtocolError::TrailingBytes)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Verifica la ida y vuelta de todos los tipos de mensaje del protocolo.
    #[test]
    fn prueba_ida_y_vuelta_de_todos_los_mensajes() {
        let mensajes = vec![
            ChannelMessage::NotificationReply {
                notification_id: "notif-1".into(),
                reply_text: "Voy en camino | llego 14:30".into(),
            },
            ChannelMessage::NotificationMarkRead {
                notification_id: "notif-2".into(),
            },
            ChannelMessage::NotificationDismiss {
                notification_id: "notif-3".into(),
            },
            ChannelMessage::NotificationPosted {
                notification_id: "notif-4".into(),
                app_name: "WhatsApp".into(),
                package_name: "com.whatsapp".into(),
                sender_title: "Equipo UPIICSA".into(),
                message_body: "¿Llegas a la reunión?".into(),
                supports_inline_reply: true,
            },
            ChannelMessage::ClipboardText {
                origin: ContentOrigin::Mobile,
                text: "Código: ñandú ✓".into(),
            },
            ChannelMessage::CameraMicCommand {
                camera_enabled: true,
                microphone_enabled: false,
                use_back_camera: true,
            },
            ChannelMessage::MusicModeCommand {
                enabled: true,
                gain_milli: 1150,
            },
            ChannelMessage::HandshakeHello {
                direction: LinkDirection::MobileToDesktop,
                device_name: "Pixel 8 Pro".into(),
                pairing_pin: 482_913,
                protocol_version: PROTOCOL_VERSION,
            },
            ChannelMessage::Heartbeat {
                timestamp_ms: 1_700_000_000_123,
            },
        ];
        for mensaje in mensajes {
            let bytes = mensaje.encode().expect("Debe codificar");
            assert_eq!(ChannelMessage::decode(&bytes), Ok(mensaje));
        }
    }

    /// Fija el vector binario de referencia compartido con las pruebas Kotlin de ChannelMessageCodec.
    #[test]
    fn prueba_vector_binario_compartido_con_kotlin() {
        let bytes = ChannelMessage::NotificationReply {
            notification_id: "n1".into(),
            reply_text: "Ok".into(),
        }
        .encode()
        .unwrap();
        assert_eq!(bytes, vec![0x10, 0, 2, b'n', b'1', 0, 2, b'O', b'k']);
        let musica = ChannelMessage::MusicModeCommand {
            enabled: true,
            gain_milli: 1150,
        }
        .encode()
        .unwrap();
        assert_eq!(musica, vec![0x40, 1, 0x04, 0x7E]);
        let saludo = ChannelMessage::HandshakeHello {
            direction: LinkDirection::MobileToDesktop,
            device_name: "Px".into(),
            pairing_pin: 482_913,
            protocol_version: 1,
        }
        .encode()
        .unwrap();
        assert_eq!(
            saludo,
            vec![0x50, 2, 0, 2, b'P', b'x', 0, 0x07, 0x5E, 0x61, 0, 1]
        );
        let aviso = ChannelMessage::NotificationPosted {
            notification_id: "n1".into(),
            app_name: "Wa".into(),
            package_name: "w".into(),
            sender_title: "Al".into(),
            message_body: "Hi".into(),
            supports_inline_reply: true,
        }
        .encode()
        .unwrap();
        assert_eq!(
            aviso,
            vec![
                0x13, 0, 2, b'n', b'1', 0, 2, b'W', b'a', 0, 1, b'w', 0, 2, b'A', b'l', 0, 2, b'H',
                b'i', 1
            ]
        );
    }

    /// Verifica el rechazo de cargas útiles malformadas.
    #[test]
    fn prueba_rechazo_de_mensajes_malformados() {
        assert_eq!(
            ChannelMessage::decode(&[]),
            Err(ProtocolError::EmptyPayload)
        );
        assert_eq!(
            ChannelMessage::decode(&[0x99]),
            Err(ProtocolError::UnknownTag)
        );
        assert_eq!(
            ChannelMessage::decode(&[0x11, 0, 5, b'a']),
            Err(ProtocolError::Truncated)
        );
        assert_eq!(
            ChannelMessage::decode(&[0x11, 0, 2, 0xFF, 0xFE]),
            Err(ProtocolError::InvalidUtf8)
        );
        assert_eq!(
            ChannelMessage::decode(&[0x30, 1, 2, 0]),
            Err(ProtocolError::InvalidEnumValue)
        );
        assert_eq!(
            ChannelMessage::decode(&[0x11, 0, 1, b'a', 0]),
            Err(ProtocolError::TrailingBytes)
        );
    }

    /// Verifica que cada mensaje se asigne a su canal lógico correcto.
    #[test]
    fn prueba_asignacion_de_canales() {
        assert_eq!(
            ChannelMessage::Heartbeat { timestamp_ms: 1 }.channel(),
            ServiceChannel::SessionControl
        );
        assert_eq!(
            ChannelMessage::ClipboardText {
                origin: ContentOrigin::Desktop,
                text: String::new()
            }
            .channel(),
            ServiceChannel::ClipboardSync
        );
    }
}
