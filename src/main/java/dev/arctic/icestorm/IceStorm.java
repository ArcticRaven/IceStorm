package dev.arctic.icestorm;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import lombok.Getter;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class IceStorm extends JavaPlugin {

    @Getter
    private static IceStorm instance;

    public IceStorm(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup(){

    }

    @Override
    protected void start() {

    }

    @Override
    protected void shutdown() {
    }


}
