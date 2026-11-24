use crate::transport::TransportMedium;

/// Motivo por el cual el supervisor decidió cambiar de medio de transporte.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SwitchReason {
    InitialSelection,
    ActiveLinkLost,
    HigherPriorityRestored,
}

/// Resultado de evaluar el estado de los enlaces en un instante dado.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LinkDecision {
    Stay(TransportMedium),
    Switch {
        from: Option<TransportMedium>,
        to: TransportMedium,
        reason: SwitchReason,
    },
    NoLink,
}

/// Parámetros de tiempo del supervisor de enlace.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LinkSupervisorConfig {
    pub heartbeat_timeout_ms: u64,
    pub upgrade_stability_ms: u64,
}

impl Default for LinkSupervisorConfig {
    /// Tiempos por defecto: 3 s sin latido declara caído un medio y 2 s de estabilidad para regresar a uno prioritario.
    fn default() -> Self {
        Self {
            heartbeat_timeout_ms: 3_000,
            upgrade_stability_ms: 2_000,
        }
    }
}

/// Estado de salud observado de un medio físico.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
struct MediumHealth {
    available: bool,
    last_heartbeat_ms: Option<u64>,
    healthy_since_ms: Option<u64>,
}

/// Máquina de estados que conmuta entre USB, Wi-Fi y Bluetooth según disponibilidad y latidos, con histéresis al regresar.
#[derive(Debug, Clone)]
pub struct LinkSupervisor {
    config: LinkSupervisorConfig,
    health: [MediumHealth; 3],
    active: Option<TransportMedium>,
}

impl LinkSupervisor {
    /// Crea un supervisor sin enlace activo con la configuración indicada.
    pub fn new(config: LinkSupervisorConfig) -> Self {
        Self {
            config,
            health: [MediumHealth::default(); 3],
            active: None,
        }
    }

    /// Medio actualmente activo, si existe.
    pub fn active_medium(&self) -> Option<TransportMedium> {
        self.active
    }

    /// Registra la conexión o desconexión física de un medio (por ejemplo, cable USB enchufado o red Wi-Fi perdida).
    pub fn report_availability(&mut self, medium: TransportMedium, available: bool, now_ms: u64) {
        let entry = &mut self.health[Self::index(medium)];
        entry.available = available;
        if available {
            entry.last_heartbeat_ms = Some(now_ms);
        } else {
            entry.last_heartbeat_ms = None;
            entry.healthy_since_ms = None;
        }
    }

    /// Registra un latido recibido por el medio indicado.
    pub fn record_heartbeat(&mut self, medium: TransportMedium, now_ms: u64) {
        let entry = &mut self.health[Self::index(medium)];
        if entry.available {
            entry.last_heartbeat_ms = Some(now_ms);
        }
    }

    /// Evalúa la salud de los medios y decide si mantener, conmutar o declarar sin enlace.
    pub fn evaluate(&mut self, now_ms: u64) -> LinkDecision {
        self.refresh_health(now_ms);
        let best_healthy = TransportMedium::by_priority()
            .into_iter()
            .find(|medium| self.is_healthy(*medium));

        let Some(active) = self.active else {
            return match best_healthy {
                Some(to) => self.switch_to(to, SwitchReason::InitialSelection),
                None => LinkDecision::NoLink,
            };
        };

        if !self.is_healthy(active) {
            return match best_healthy {
                Some(to) => self.switch_to(to, SwitchReason::ActiveLinkLost),
                None => {
                    self.active = None;
                    LinkDecision::NoLink
                }
            };
        }

        let upgrade = TransportMedium::by_priority()
            .into_iter()
            .take_while(|medium| *medium != active)
            .find(|medium| self.is_stable(*medium, now_ms));
        match upgrade {
            Some(to) => self.switch_to(to, SwitchReason::HigherPriorityRestored),
            None => LinkDecision::Stay(active),
        }
    }

    /// Actualiza las marcas de salud según el tiempo transcurrido desde el último latido.
    fn refresh_health(&mut self, now_ms: u64) {
        let timeout = self.config.heartbeat_timeout_ms;
        for entry in self.health.iter_mut() {
            let alive = entry.available
                && entry
                    .last_heartbeat_ms
                    .is_some_and(|last| now_ms.saturating_sub(last) <= timeout);
            if alive {
                entry.healthy_since_ms.get_or_insert(now_ms);
            } else {
                entry.healthy_since_ms = None;
            }
        }
    }

    /// Indica si un medio está sano en la última evaluación.
    fn is_healthy(&self, medium: TransportMedium) -> bool {
        self.health[Self::index(medium)].healthy_since_ms.is_some()
    }

    /// Indica si un medio lleva sano al menos la ventana de estabilidad configurada.
    fn is_stable(&self, medium: TransportMedium, now_ms: u64) -> bool {
        self.health[Self::index(medium)]
            .healthy_since_ms
            .is_some_and(|since| now_ms.saturating_sub(since) >= self.config.upgrade_stability_ms)
    }

    /// Aplica la conmutación al medio indicado y construye la decisión correspondiente.
    fn switch_to(&mut self, to: TransportMedium, reason: SwitchReason) -> LinkDecision {
        let from = self.active.replace(to);
        LinkDecision::Switch { from, to, reason }
    }

    /// Índice interno del arreglo de salud para cada medio.
    fn index(medium: TransportMedium) -> usize {
        medium as usize - 1
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Verifica la selección inicial por prioridad, la caída del USB y el regreso con histéresis.
    #[test]
    fn prueba_conmutacion_usb_wifi_y_regreso() {
        let mut sup = LinkSupervisor::new(LinkSupervisorConfig::default());
        assert_eq!(sup.evaluate(0), LinkDecision::NoLink);

        sup.report_availability(TransportMedium::UsbCable, true, 0);
        sup.report_availability(TransportMedium::WifiLan, true, 0);
        assert_eq!(
            sup.evaluate(10),
            LinkDecision::Switch {
                from: None,
                to: TransportMedium::UsbCable,
                reason: SwitchReason::InitialSelection
            }
        );

        sup.report_availability(TransportMedium::UsbCable, false, 100);
        sup.record_heartbeat(TransportMedium::WifiLan, 100);
        assert_eq!(
            sup.evaluate(110),
            LinkDecision::Switch {
                from: Some(TransportMedium::UsbCable),
                to: TransportMedium::WifiLan,
                reason: SwitchReason::ActiveLinkLost
            }
        );

        sup.report_availability(TransportMedium::UsbCable, true, 1_000);
        sup.record_heartbeat(TransportMedium::WifiLan, 1_000);
        assert_eq!(
            sup.evaluate(1_000),
            LinkDecision::Stay(TransportMedium::WifiLan)
        );
        sup.record_heartbeat(TransportMedium::UsbCable, 2_500);
        sup.record_heartbeat(TransportMedium::WifiLan, 2_500);
        assert_eq!(
            sup.evaluate(2_500),
            LinkDecision::Stay(TransportMedium::WifiLan)
        );
        sup.record_heartbeat(TransportMedium::UsbCable, 3_100);
        sup.record_heartbeat(TransportMedium::WifiLan, 3_100);
        assert_eq!(
            sup.evaluate(3_100),
            LinkDecision::Switch {
                from: Some(TransportMedium::WifiLan),
                to: TransportMedium::UsbCable,
                reason: SwitchReason::HigherPriorityRestored
            }
        );
    }

    /// Verifica que la ausencia de latidos degrade hasta Bluetooth y finalmente a sin enlace.
    #[test]
    fn prueba_degradacion_por_latidos_vencidos() {
        let mut sup = LinkSupervisor::new(LinkSupervisorConfig {
            heartbeat_timeout_ms: 500,
            upgrade_stability_ms: 1_000,
        });
        sup.report_availability(TransportMedium::WifiLan, true, 0);
        sup.report_availability(TransportMedium::BluetoothRfcomm, true, 0);
        sup.evaluate(0);
        assert_eq!(sup.active_medium(), Some(TransportMedium::WifiLan));

        sup.record_heartbeat(TransportMedium::BluetoothRfcomm, 600);
        assert_eq!(
            sup.evaluate(700),
            LinkDecision::Switch {
                from: Some(TransportMedium::WifiLan),
                to: TransportMedium::BluetoothRfcomm,
                reason: SwitchReason::ActiveLinkLost
            }
        );
        assert_eq!(sup.evaluate(5_000), LinkDecision::NoLink);
        assert_eq!(sup.active_medium(), None);
    }
}
