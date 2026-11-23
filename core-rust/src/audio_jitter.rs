use std::collections::BTreeMap;

/// Telemetría acumulada del búfer de fluctuación (jitter) de audio.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct JitterStats {
    pub frames_played: u64,
    pub frames_concealed: u64,
    pub late_drops: u64,
    pub overflow_drops: u64,
    pub underruns: u64,
}

/// Búfer de fluctuación que reordena bloques PCM de Music Mode/micrófono por número de secuencia y oculta pérdidas con silencio.
#[derive(Debug, Clone)]
pub struct PcmJitterBuffer {
    frames: BTreeMap<u32, Vec<i16>>,
    next_sequence: Option<u32>,
    samples_per_frame: usize,
    target_depth: usize,
    max_depth: usize,
    primed: bool,
    stats: JitterStats,
}

impl PcmJitterBuffer {
    /// Crea un búfer con el tamaño de bloque, la profundidad objetivo antes de reproducir y la profundidad máxima.
    pub fn new(samples_per_frame: usize, target_depth: usize, max_depth: usize) -> Self {
        Self {
            frames: BTreeMap::new(),
            next_sequence: None,
            samples_per_frame,
            target_depth: target_depth.max(1),
            max_depth: max_depth.max(target_depth.max(1)),
            primed: false,
            stats: JitterStats::default(),
        }
    }

    /// Cantidad de bloques almacenados en espera de reproducción.
    pub fn depth(&self) -> usize {
        self.frames.len()
    }

    /// Devuelve la telemetría acumulada.
    pub fn stats(&self) -> JitterStats {
        self.stats
    }

    /// Inserta un bloque recibido; descarta duplicados, bloques tardíos y el más antiguo si se excede la capacidad.
    pub fn push(&mut self, sequence_id: u32, samples: Vec<i16>) {
        if self.next_sequence.is_some_and(|next| sequence_id < next) {
            self.stats.late_drops += 1;
            return;
        }
        self.frames.entry(sequence_id).or_insert(samples);
        while self.frames.len() > self.max_depth {
            if let Some((&oldest, _)) = self.frames.iter().next() {
                self.frames.remove(&oldest);
                self.stats.overflow_drops += 1;
                if self.next_sequence.is_some_and(|next| next <= oldest) {
                    self.next_sequence = Some(oldest + 1);
                }
            }
        }
    }

    /// Obtiene el siguiente bloque a reproducir: real, silencio de ocultamiento ante hueco, o `None` si aún no se llena.
    pub fn pop(&mut self) -> Option<Vec<i16>> {
        if !self.primed {
            if self.frames.len() < self.target_depth {
                return None;
            }
            self.primed = true;
        }
        let Some((&first_buffered, _)) = self.frames.iter().next() else {
            self.stats.underruns += 1;
            self.primed = false;
            return None;
        };
        let expected = *self.next_sequence.get_or_insert(first_buffered);
        self.next_sequence = Some(expected.wrapping_add(1));
        match self.frames.remove(&expected) {
            Some(samples) => {
                self.stats.frames_played += 1;
                Some(samples)
            }
            None => {
                self.stats.frames_concealed += 1;
                Some(vec![0_i16; self.samples_per_frame])
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Verifica el reordenamiento de bloques recibidos fuera de orden.
    #[test]
    fn prueba_reordenamiento_por_secuencia() {
        let mut jb = PcmJitterBuffer::new(2, 3, 8);
        jb.push(11, vec![2, 2]);
        jb.push(10, vec![1, 1]);
        assert_eq!(jb.pop(), None);
        jb.push(12, vec![3, 3]);
        assert_eq!(jb.pop(), Some(vec![1, 1]));
        assert_eq!(jb.pop(), Some(vec![2, 2]));
        assert_eq!(jb.pop(), Some(vec![3, 3]));
        assert_eq!(jb.stats().frames_played, 3);
    }

    /// Verifica que un hueco se oculte con silencio y que los bloques tardíos se descarten.
    #[test]
    fn prueba_ocultamiento_y_descarte_tardio() {
        let mut jb = PcmJitterBuffer::new(3, 2, 8);
        jb.push(1, vec![5, 5, 5]);
        jb.push(3, vec![7, 7, 7]);
        assert_eq!(jb.pop(), Some(vec![5, 5, 5]));
        assert_eq!(jb.pop(), Some(vec![0, 0, 0]));
        jb.push(2, vec![6, 6, 6]);
        assert_eq!(jb.pop(), Some(vec![7, 7, 7]));
        assert_eq!(jb.stats().frames_concealed, 1);
        assert_eq!(jb.stats().late_drops, 1);
        assert_eq!(jb.pop(), None);
        assert_eq!(jb.stats().underruns, 1);
    }

    /// Verifica el descarte del bloque más antiguo al exceder la profundidad máxima.
    #[test]
    fn prueba_desbordamiento() {
        let mut jb = PcmJitterBuffer::new(1, 1, 2);
        jb.push(1, vec![1]);
        jb.push(2, vec![2]);
        jb.push(3, vec![3]);
        assert_eq!(jb.depth(), 2);
        assert_eq!(jb.stats().overflow_drops, 1);
        assert_eq!(jb.pop(), Some(vec![2]));
    }
}
