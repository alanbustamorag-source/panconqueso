#include "effects_readiness.hpp"
#include <vector>
#include <functional>
#include <iostream>
int main() {
    unsigned checks=0;
    auto check=[&](bool ok){++checks;if(!ok)throw std::runtime_error("readiness expectation failed");};
    unsigned calls=0,notices=0,pauses=0;std::uint64_t tick=0;
    bodrio::wait_for_effects([&]{++calls;return calls==1?1237u:calls==2?170u:0u;},[&]{return tick;},[&](unsigned ms){++pauses;tick+=ms;},[]{return false;},[&]{++notices;});
    check(calls==3);check(notices==2);check(pauses==2 && tick==500);
    calls=0;bodrio::wait_for_effects([&]{++calls;return 0u;},[&]{return tick;},[](unsigned){throw std::runtime_error("unexpected pause");},[]{return false;},[]{});check(calls==1);
    auto rejects=[&](const std::function<void()>& run,const std::string& text){bool yes=false;try{run();}catch(const std::exception& e){yes=std::string(e.what()).find(text)!=std::string::npos;}check(yes);};
    rejects([&]{bodrio::wait_for_effects([]{return 1306u;},[]{return 0ull;},[](unsigned){},[]{return false;},[]{});},"1.26.60.5");
    rejects([&]{bodrio::wait_for_effects([]{return 29u;},[]{return 0ull;},[](unsigned){},[]{return false;},[]{});},"Windows 29");
    tick=0;calls=0;rejects([&]{bodrio::wait_for_effects([&]{++calls;return 1237u;},[&]{return tick;},[&](unsigned ms){tick+=ms;},[]{return false;},[]{},1000);},"no terminó");check(tick==1000 && calls==5);
    tick=0;calls=0;rejects([&]{bodrio::wait_for_effects([&]{++calls;return 1237u;},[&]{return tick;},[&](unsigned ms){tick+=ms;},[&]{return tick>=500;},[]{});},"cancelada");check(calls==2);
    std::cout<<checks<<" native readiness/retry/cancellation checks passed\n";
}
