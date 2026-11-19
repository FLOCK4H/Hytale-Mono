package org.example.plugin;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.*;


final class PageKeyboard {
    private PageKeyboard(){}
    static boolean closes(String value){return " ".equals(value)||"\t".equals(value);}
    static void bind(UIEventBuilder events,String selector){events.addEventBinding(CustomUIEventBindingType.ValueChanged,selector,new EventData().append("Action","Keyboard").append("@KeyText",selector+".Value"),false);}
    static void reset(UICommandBuilder ui,String selector){ui.set(selector+".Value","");}
    static void focus(UICommandBuilder ui,String selector){ui.set(selector+".AutoFocus",true);}
}
