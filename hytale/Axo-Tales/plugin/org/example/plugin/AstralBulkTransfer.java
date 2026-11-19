package org.example.plugin;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.io.IOException;
import java.util.*;


final class AstralBulkTransfer {
    record Source(ItemContainer container,short slot,ItemStack expected){}
    private AstralBulkTransfer(){}
    static long deposit(AstralVault.Vault vault,long revision,List<Source> visible,boolean quickstack) throws IOException {
        AstralVault.TRANSACTIONS.lock();try {
            var before=vault.snapshot();if(before.revision()!=revision)return 0;
            var types=new HashSet<String>();for(var entry:before.entries())types.add(entry.item().getItemId());
            long moved=0;
            for(var source:visible){
                if(quickstack&&!types.contains(source.expected.getItemId()))continue;
                moved+=vault.deposit(vault.snapshot().revision(),source.container,source.slot,source.expected,Integer.MAX_VALUE);
            }
            return moved;
        }finally{AstralVault.TRANSACTIONS.unlock();}
    }
    static long withdraw(AstralVault.Vault vault,long revision,List<UUID> visible,List<ItemContainer> destinations) throws IOException {
        AstralVault.TRANSACTIONS.lock();try {
            if(vault.snapshot().revision()!=revision)return 0;long moved=0;
            for(var id:visible){int amount;do {amount=vault.withdrawAll(vault.snapshot().revision(),id,destinations);moved+=amount;}while(amount==Integer.MAX_VALUE);}
            return moved;
        }finally{AstralVault.TRANSACTIONS.unlock();}
    }
}
