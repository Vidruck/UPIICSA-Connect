pub mod ffi_bridge;
pub mod signal_dsp;
pub mod transport;

#[cfg(test)]
mod tests {
    use super::ffi_bridge::{
        upiicsa_compute_frame_crc32, upiicsa_probe_usb_device, upiicsa_process_music_mode_pcm,
        upiicsa_select_transport_medium,
    };
    use super::signal_dsp::{MusicModeDspConfig, SignalDspProcessor, VideoChunkHeader};
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
        let decoded = TransportMultiplexer::decode_wire_bytes(&encoded).expect("Debe decodificar trama valida");
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
        let clipped = unsafe { upiicsa_process_music_mode_pcm(audio.as_mut_ptr(), audio.len(), 2.0) };
        assert_eq!(clipped, 0);
        assert_eq!(audio[0], 1000);

        let data = b"portapapeles_upiicsa";
        let crc = unsafe { upiicsa_compute_frame_crc32(data.as_ptr(), data.len(), 2, 1) };
        assert_ne!(crc, 0);
    }
}
