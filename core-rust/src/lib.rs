//! Core nativo de UPIICSA-Connect: transporte priorizado, protocolo de canales, DSP y puente FFI.

pub mod audio_jitter;
pub mod ffi_bridge;
#[cfg(target_os = "android")]
pub mod jni_bridge;
pub mod link_supervisor;
pub mod pairing;
pub mod protocol;
pub mod signal_dsp;
pub mod stream_decoder;
pub mod transport;

#[cfg(test)]
mod tests {
    use super::ffi_bridge::{
        upiicsa_compute_frame_crc32, upiicsa_core_version, upiicsa_derive_pairing_pin,
        upiicsa_encode_frame, upiicsa_probe_usb_device, upiicsa_process_music_mode_pcm,
        upiicsa_select_transport_medium, upiicsa_validate_wire_frame,
    };
    use super::pairing::derive_pairing_pin;
    use super::signal_dsp::{MusicModeDspConfig, SignalDspProcessor, VideoChunkHeader};
    use super::transport::compute_crc32;
    use super::transport::{ServiceChannel, TransportMedium, TransportMultiplexer};

    /// Verifica que el multiplexor siempre elija Cable USB antes que Wi-Fi o Bluetooth.
    #[test]
    fn prueba_prioridad_estricta_usb_sobre_inalambrico() {
        let mux = TransportMultiplexer::new();
        assert_eq!(
            mux.select_best_medium(true, true, true),
            Some(TransportMedium::UsbCable)
        );
        assert_eq!(
            mux.select_best_medium(false, true, true),
            Some(TransportMedium::WifiLan)
        );
        assert_eq!(
            mux.select_best_medium(false, false, true),
            Some(TransportMedium::BluetoothRfcomm)
        );
        assert_eq!(mux.select_best_medium(false, false, false), None);
    }

    /// Valida el empaquetado de una trama de resolución de notificaciones y su integridad CRC32.
    #[test]
    fn prueba_empaquetado_y_crc32_de_trama() {
        let mut mux = TransportMultiplexer::new();
        let payload = b"RESPONDER:notif-101:Voy en camino a UPIICSA".to_vec();
        let frame = mux.pack_frame(
            TransportMedium::UsbCable,
            ServiceChannel::NotificationResolution,
            payload.clone(),
        );
        assert_eq!(frame.sequence_id, 1);
        assert_eq!(frame.payload, payload);
        assert_ne!(frame.crc32, 0);

        let encoded = TransportMultiplexer::encode_wire_bytes(&frame);
        let decoded = TransportMultiplexer::decode_wire_bytes(&encoded)
            .expect("Debe decodificar trama valida");
        assert_eq!(decoded, frame);
    }

    /// Verifica la normalización de ganancia y limitador suave del DSP en Music Mode.
    #[test]
    fn prueba_dsp_music_mode_limitador_y_empaquetado() {
        let processor = SignalDspProcessor::new(MusicModeDspConfig {
            sample_rate_hz: 48_000,
            channels: 2,
            gain_multiplier: 1.5,
            limiter_ceiling: 0.95,
        });
        let mut samples = vec![1000_i16, -1000_i16, 30_000_i16, -30_000_i16];
        let telemetry = processor.process_music_mode_pcm_block(&mut samples);

        assert_eq!(telemetry.processed_samples, 4);
        assert_eq!(samples[0], 1500);
        assert_eq!(samples[1], -1500);
        assert_eq!(telemetry.clipped_samples, 2);

        let bytes = SignalDspProcessor::pack_pcm_le_bytes(&samples);
        assert_eq!(bytes.len(), 8);
    }

    /// Valida el empaquetado de cabecera de video para Monitor Secundario y Cámara Remota.
    #[test]
    fn prueba_empaquetado_cabecera_video() {
        let header = VideoChunkHeader {
            width: 1920,
            height: 1080,
            fps: 60,
            is_keyframe: true,
            timestamp_ms: 12_345,
        };
        let chunk = SignalDspProcessor::pack_video_chunk(header, &[0xAA, 0xBB, 0xCC]);
        assert_eq!(chunk.len(), 17);
        assert_eq!(chunk[4], 60);
        assert_eq!(chunk[5], 1);
    }

    /// Comprueba las funciones C-ABI expuestas al puente Kotlin Multiplatform.
    #[test]
    fn prueba_puente_ffi_kotlin_rust() {
        assert_eq!(upiicsa_select_transport_medium(1, 1, 1), 1);
        assert_eq!(upiicsa_select_transport_medium(0, 1, 1), 2);
        assert_eq!(upiicsa_probe_usb_device(1), 1);
        assert_eq!(upiicsa_probe_usb_device(0), 0);

        let mut audio = [500_i16, -500_i16];
        let clipped =
            unsafe { upiicsa_process_music_mode_pcm(audio.as_mut_ptr(), audio.len(), 2.0) };
        assert_eq!(clipped, 0);
        assert_eq!(audio[0], 1000);

        let data = b"portapapeles_upiicsa";
        let crc = unsafe { upiicsa_compute_frame_crc32(data.as_ptr(), data.len(), 2, 1) };
        assert_ne!(crc, 0);
    }

    /// Comprueba las nuevas funciones FFI de serialización, validación, versión y PIN de emparejamiento.
    #[test]
    fn prueba_puente_ffi_tramas_y_emparejamiento() {
        assert_eq!(upiicsa_core_version(), 0x00_03_00);

        let payload = b"UPIICSA";
        let mut out = [0_u8; 64];
        let written = unsafe {
            upiicsa_encode_frame(
                1,
                2,
                7,
                payload.as_ptr(),
                payload.len(),
                out.as_mut_ptr(),
                out.len(),
            )
        };
        assert_eq!(written, 23);
        let valid = unsafe { upiicsa_validate_wire_frame(out.as_ptr(), written as usize) };
        assert_eq!(valid, 2);
        out[20] ^= 0x55;
        let invalid = unsafe { upiicsa_validate_wire_frame(out.as_ptr(), written as usize) };
        assert_eq!(invalid, -7);

        let mut tiny = [0_u8; 4];
        let too_small = unsafe {
            upiicsa_encode_frame(
                1,
                2,
                7,
                payload.as_ptr(),
                payload.len(),
                tiny.as_mut_ptr(),
                tiny.len(),
            )
        };
        assert_eq!(too_small, -2);

        let d = "pc-linux-upiicsa";
        let m = "usb-pixel8-upiicsa";
        let pin =
            unsafe { upiicsa_derive_pairing_pin(d.as_ptr(), d.len(), m.as_ptr(), m.len(), 42) };
        assert_eq!(pin, derive_pairing_pin(d, m, 42));
    }

    /// Fija los vectores de referencia CRC32 y PIN compartidos con las pruebas Kotlin de DummyRustCoreBridge.
    #[test]
    fn prueba_vectores_de_referencia_compartidos() {
        assert_eq!(compute_crc32(b"UPIICSA", 2, 7), VECTOR_CRC_UPIICSA);
        assert_eq!(compute_crc32(b"", 1, 1), VECTOR_CRC_VACIO);
        assert_eq!(
            derive_pairing_pin("pc-linux-upiicsa", "usb-pixel8-upiicsa", 42),
            VECTOR_PIN
        );
    }

    /// Vector CRC32 de "UPIICSA" en canal 2 y secuencia 7.
    const VECTOR_CRC_UPIICSA: u32 = 2_972_122_761;
    /// Vector CRC32 de carga vacía en canal 1 y secuencia 1.
    const VECTOR_CRC_VACIO: u32 = 2_353_393_211;
    /// Vector de PIN para los identificadores de prueba con nonce 42.
    const VECTOR_PIN: u32 = 178_633;
}
