package org.example.plugin;

import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import java.util.*;


final class InvisibilityVisibility {
    private final Map<UUID,Set<UUID>> owned=new HashMap<>();

    synchronized int reconcile(UUID viewer,Set<UUID> invisible,HiddenPlayersManager manager) {
        var claims=owned.computeIfAbsent(viewer,key->new HashSet<>());int changed=0;
        for(var iterator=claims.iterator();iterator.hasNext();) {
            var subject=iterator.next();
            if(invisible.contains(subject)&&!subject.equals(viewer))continue;
            manager.showPlayer(subject);iterator.remove();changed++;
        }
        for(var subject:invisible) {
            if(subject.equals(viewer)||manager.isPlayerHidden(subject))continue;
            manager.hidePlayer(subject);claims.add(subject);changed++;
        }

        if(claims.isEmpty())owned.remove(viewer);
        return changed;
    }
    synchronized void releaseSubject(UUID viewer,UUID subject,HiddenPlayersManager manager) {
        var claims=owned.get(viewer);
        if(claims!=null&&claims.contains(subject)) {
            manager.showPlayer(subject);claims.remove(subject);
            if(claims.isEmpty())owned.remove(viewer);
        }
    }
    synchronized void forgetViewer(UUID viewer){owned.remove(viewer);}
}
