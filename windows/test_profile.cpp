#include "profile.hpp"
#include <iostream>
#include <cstdlib>

static unsigned checks = 0;
void check(bool yes, const char* what) {
    ++checks;
    if (!yes) { std::cerr << "FAIL: " << what << '\n'; std::exit(1); }
}
int main() {
    using namespace bodrio;
    check(supported_version(1,26,60,0), "display/file version 1.26.60");
    check(supported_version(1,26,6000,0), "Windows package style 1.26.6000");
    check(supported_version(1,26,63,1), "latest verified Windows hotfix");
    check(supported_version(1,26,6300,0), "encoded Windows hotfix");
    check(supported_version(26,62,1,0), "year-based hotfix");
    check(!supported_effects_version(1,26,63,1), "never reuse old offsets for new release");
    check(!supported_version(1,26,70,26), "unverified preview release");
    check(supported_version(1,26,6001,0), "Windows package servicing 6001");
    check(supported_version(26,60,0,0), "year-based file version");
    check(!supported_version(1,26,6100,0), "refuse next game release");
    check(!supported_version(1,26,5900,0), "refuse previous game release");
    check(!supported_version(1,21,6000,0), "refuse 1.21.60");
    check(!supported_version(26,61,0,0), "refuse other year-based release");
    check(game_display_version(1,26,60,0)=="26.60", "display actual direct 26.60 file version");
    check(game_display_version(1,26,6000,0)=="26.60", "display actual encoded 26.60 file version");
    check(game_display_version(1,26,6100,0)=="26.61", "display incompatible actual version instead of compatible target");
    check(game_display_version(1,26,6001,0)=="26.60.1", "retain encoded patch version");
    check(game_display_version(1,26,60,3)=="26.60.3", "retain direct patch version");
    check(game_display_version(26,60,0,0)=="26.60", "display year-based file version");
    check(game_display_version(26,61,1,2)=="26.61.1.2", "display other year-based version with revision");
    check(game_display_version(1,21,13000,0)=="1.21.130", "display older actual version");
    check(game_display_version(1,26,6000,2)=="26.60.0.2", "retain packaging revision without inventing patch");
    auto def = configure_nether("");
    check(def.find("[Nether]\r\nmin=0\r\nmax=256") != std::string::npos, "default Nether profile");
    check(def.find("min=-64\r\nmax=320") != std::string::npos, "default Overworld");
    check(configure_nether(def) == def, "idempotent default");
    std::string custom = "# notes\nhook_version=2\n[Overworld]\nmin=-128\nmax=512\n\n[Nether]\nmin=-16\nmax=128 # ceiling\nfoo=bar\n[MyWorld]\nmin=0\nmax=1024\n";
    auto updated = configure_nether(custom);
    check(updated.find("[Overworld]\nmin=-128\nmax=512") != std::string::npos, "preserve custom Overworld");
    check(updated.find("[MyWorld]\nmin=0\nmax=1024") != std::string::npos, "preserve extra dimension");
    check(updated.find("hook_version=2") != std::string::npos, "preserve hook override");
    check(updated.find("max=256 # ceiling") != std::string::npos, "preserve inline comment");
    check(updated.find("foo=bar") != std::string::npos, "preserve unknown keys");
    check(configure_nether(updated) == updated, "idempotent custom profile");
    check(configure_nether("[Nether]\nmax=128\n").find("min=0") != std::string::npos, "insert missing min");
    check(configure_nether("[Nether]\nmin=0\n").find("max=256") != std::string::npos, "insert missing max");
    check(configure_nether("[Nether]\n[TheEnd]\nmax=512\n").find("[Nether]\nmin=0\nmax=256\n[TheEnd]") != std::string::npos, "empty section before next");
    auto duplicates=configure_nether("[Nether]\nmax=128\nmax=144\n[Nether]\nmin=-16\nmax=128\n");
    check(duplicates.find("max=128") == std::string::npos && duplicates.find("max=144") == std::string::npos, "update every repeated key/section");
    auto absent = configure_nether("[Overworld]\nmin=-64\nmax=320");
    check(absent.find("[Nether]\nmin=0\nmax=256\n") != std::string::npos, "append missing section without final newline");
    check(configure_nether("\xef\xbb\xbf[Nether]\nmax=128").find("\xef\xbb\xbf") == std::string::npos, "remove incompatible UTF-8 BOM");
    check(configure_nether("[Nether]\r\nmin=0\r\nmax=128\r\n").find("\r\n") != std::string::npos, "preserve CRLF");
    check(configure_nether("[Nether] # comment\nmax=128\n").find("max=256") != std::string::npos, "commented section");
    bool rejected=false;
    try { configure_nether(std::string("a\0b",3)); } catch (...) { rejected=true; }
    check(rejected, "reject binary/UTF-16 configuration");
    rejected=false; try { configure_nether("[Nether\nmax=128"); } catch (...) { rejected=true; }
    check(rejected, "reject malformed section before writing");
    rejected=false; try { configure_nether(std::string(1024*1024+1,'a')); } catch (...) { rejected=true; }
    check(rejected, "reject oversized profile");
    rejected=false; try { configure_nether("[Nether]\ntexto sin clave ni valor\n"); } catch (...) { rejected=true; }
    check(rejected, "reject invalid lines instead of letting native parser reset defaults");
    std::cout << checks << " profile/version checks passed\n";
}
