/// Configuración de audio utilizada por Music Mode para descargar el procesamiento multimedia hacia el teléfono.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct MusicModeDspConfig {
    pub sample_rate_hz: u32,
    pub channels: u8,
    pub gain_multiplier: f32,
    pub limiter_ceiling: f32,
}

impl Default for MusicModeDspConfig {
    /// Provee la configuración estándar de 48 kHz estéreo sin distorsión para Music Mode.
    fn default() -> Self {
        Self {
            sample_rate_hz: 48_000,
            channels: 2,
            gain_multiplier: 1.15,
            limiter_ceiling: 0.98,
        }
    }
}

/// Métricas resultantes del procesamiento DSP de un bloque PCM para telemetría en la interfaz gráfica.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioBlockTelemetry {
    pub processed_samples: usize,
    pub peak_amplitude: f32,
    pub clipped_samples: usize,
}

/// Metadatos de empaquetado de video de baja latencia para Monitor Secundario y Cámara Remota.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct VideoChunkHeader {
    pub width: u16,
    pub height: u16,
    pub fps: u8,
    pub is_keyframe: bool,
    pub timestamp_ms: u64,
}

/// Procesador de señales digitales encargado de optimizar el flujo de audio de Music Mode, micrófono remoto y tramas de video.
#[derive(Debug, Clone)]
pub struct SignalDspProcessor {
    music_config: MusicModeDspConfig,
}

impl SignalDspProcessor {
    /// Crea un nuevo procesador DSP con los parámetros iniciales de Music Mode.
    pub fn new(music_config: MusicModeDspConfig) -> Self {
        Self { music_config }
    }

    /// Aplica ganancia controlada y limitación suave sobre muestras PCM i16 de Music Mode para potenciar el sonido en la tarjeta de PC.
    pub fn process_music_mode_pcm_block(&self, samples: &mut [i16]) -> AudioBlockTelemetry {
        let mut peak: f32 = 0.0;
        let mut clipped_samples: usize = 0;
        let max_limit = (i16::MAX as f32) * self.music_config.limiter_ceiling;

        for sample in samples.iter_mut() {
            let scaled = (*sample as f32) * self.music_config.gain_multiplier;
            let abs_scaled = scaled.abs();
            if abs_scaled > peak {
                peak = abs_scaled;
            }
            let clamped = if scaled > max_limit {
                clipped_samples += 1;
                max_limit
            } else if scaled < -max_limit {
                clipped_samples += 1;
                -max_limit
            } else {
                scaled
            };
            *sample = clamped.round() as i16;
        }

        AudioBlockTelemetry {
            processed_samples: samples.len(),
            peak_amplitude: peak / (i16::MAX as f32),
            clipped_samples,
        }
    }

    /// Convierte muestras PCM i16 procesadas en bytes Little-Endian listos para enviarse a PipeWire o WASAPI.
    pub fn pack_pcm_le_bytes(samples: &[i16]) -> Vec<u8> {
        let mut out = Vec::with_capacity(samples.len() * 2);
        for &sample in samples {
            out.extend_from_slice(&sample.to_le_bytes());
        }
        out
    }

    /// Empaqueta un fragmento de cuadro de video (Monitor Secundario o Webcam Android) con su cabecera binaria de sincronización.
    pub fn pack_video_chunk(header: VideoChunkHeader, frame_payload: &[u8]) -> Vec<u8> {
        let mut out = Vec::with_capacity(14 + frame_payload.len());
        out.extend_from_slice(&header.width.to_be_bytes());
        out.extend_from_slice(&header.height.to_be_bytes());
        out.push(header.fps);
        out.push(u8::from(header.is_keyframe));
        out.extend_from_slice(&header.timestamp_ms.to_be_bytes());
        out.extend_from_slice(frame_payload);
        out
    }
}
