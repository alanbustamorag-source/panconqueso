#pragma once
#include <cstdint>
#include <stdexcept>
#include <string>
namespace bodrio {
template<class Invoke,class Clock,class Pause,class Cancel,class Report>
void wait_for_effects(Invoke invoke,Clock clock,Pause pause,Cancel cancel,Report report,std::uint64_t timeout=45000) {
    const auto started=clock();
    while(true) {
        if(cancel()) throw std::runtime_error("Preparación cancelada.");
        const std::uint32_t result=invoke();
        if(!result) return;
        if(result==1306) throw std::runtime_error("Anticombustión requiere el ejecutable exacto de Minecraft 1.26.60.5. El módulo rechazó esta versión antes de aplicar sus cambios.");
        if(result!=1237 && result!=170) throw std::runtime_error("No se completó Anticombustión (Windows "+std::to_string(result)+"). Revisa BodriosafioEffects-status.json en la carpeta runtime del launcher.");
        if(clock()-started>=timeout) throw std::runtime_error("Minecraft no terminó de iniciar. Espera en el menú principal y vuelve a pulsar Jugar.");
        report();pause(250);
    }
}
}
