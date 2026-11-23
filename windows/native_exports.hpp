#pragma once
#include <algorithm>
#include <cstdint>
#include <stdexcept>
#include <string>

namespace bodrio {
namespace pe_detail {
inline void range(const std::string& b,std::uint64_t p,std::uint64_t n) {
    if(p>b.size() || n>b.size()-p) throw std::runtime_error("La DLL incluida tiene una estructura PE inválida.");
}
inline std::uint16_t u16(const std::string& b,std::uint64_t p) {
    range(b,p,2);return static_cast<unsigned char>(b[p]) | (static_cast<unsigned char>(b[p+1])<<8);
}
inline std::uint32_t u32(const std::string& b,std::uint64_t p) {
    range(b,p,4);return u16(b,p) | (std::uint32_t(u16(b,p+2))<<16);
}
}
// Resolve an export without loading the game module into the launcher process.
inline std::uint32_t native_export_rva(const std::string& b,const std::string& name) {
    using namespace pe_detail;
    if(u16(b,0)!=0x5a4d) throw std::runtime_error("La DLL incluida no es válida.");
    std::uint64_t nt=u32(b,0x3c),opt=nt+24;
    if(u32(b,nt)!=0x4550 || u16(b,nt+4)!=0x8664 || u16(b,opt)!=0x20b || u16(b,nt+20)<120)
        throw std::runtime_error("La DLL de Anticombustión debe ser Windows x64.");
    auto count=u16(b,nt+6);std::uint64_t sections=opt+u16(b,nt+20);
    if(!count || count>128) throw std::runtime_error("La DLL incluida tiene secciones inválidas.");
    range(b,sections,std::uint64_t(count)*40);
    auto raw=[&](std::uint32_t rva,std::uint32_t length=1)->std::uint64_t {
        if(rva<u32(b,opt+60)) {range(b,rva,length);return rva;}
        for(unsigned i=0;i<count;++i) {
            auto s=sections+i*40;auto va=u32(b,s+12),size=u32(b,s+16);
            if(rva>=va && std::uint64_t(rva)-va+length<=size) {
                std::uint64_t offset=u32(b,s+20)+std::uint64_t(rva)-va;range(b,offset,length);return offset;
            }
        }
        throw std::runtime_error("La DLL incluida contiene una dirección fuera del archivo.");
    };
    auto er=u32(b,opt+112),es=u32(b,opt+116);auto e=raw(er,40);
    auto functions=u32(b,e+20),names=u32(b,e+24);
    if(!functions || functions>4096 || names>4096) throw std::runtime_error("Las exportaciones de Anticombustión no son válidas.");
    auto ft=raw(u32(b,e+28),functions*4),ntable=raw(u32(b,e+32),names*4),ot=raw(u32(b,e+36),names*2);
    for(std::uint32_t i=0;i<names;++i) {
        auto p=raw(u32(b,ntable+i*4));range(b,p,name.size()+1);
        if(b.compare(p,name.size(),name)!=0 || b[p+name.size()]!='\0') continue;
        auto ordinal=u16(b,ot+i*2);
        if(ordinal>=functions) throw std::runtime_error("La DLL tiene un ordinal inválido.");
        auto address=u32(b,ft+ordinal*4);
        if(address>=er && std::uint64_t(address)-er<es) throw std::runtime_error("La exportación de Anticombustión no es una función local.");
        raw(address);
        for(unsigned j=0;j<count;++j) {
            auto s=sections+j*40;auto va=u32(b,s+12);
            if(address>=va && std::uint64_t(address)-va<u32(b,s+16) && (u32(b,s+36)&0x20000000)) return address;
        }
        throw std::runtime_error("La inicialización de Anticombustión no apunta a código ejecutable.");
    }
    throw std::runtime_error("Falta la función de inicialización de Anticombustión.");
}
}
