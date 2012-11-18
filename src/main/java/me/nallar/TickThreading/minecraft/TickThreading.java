package me.nallar.tickthreading.minecraft;

import java.lang.reflect.Field;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.entitylist.LoadedEntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickthreading.minecraft.entitylist.overrideList;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;

@SuppressWarnings ("WeakerAccess")
@Mod (modid = "nallar_TickThreading", name = "TickThreading", version = "1.0")
@NetworkMod (clientSideRequired = false, serverSideRequired = false)
public class TickThreading {
	// TODO: Not hardcoded field names
	private final String loadedTileEntityFieldName = "";
	private final String loadedEntityFieldName = "";

	@Mod.Init
	public void load(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		ThreadManager manager = new ThreadManager(event.world);
		try {
			Field loadedTileEntityField = World.class.getDeclaredField(loadedTileEntityFieldName);
			Field loadedEntityField = World.class.getDeclaredField(loadedEntityFieldName);
			new LoadedTileEntityList<TileEntity>(event.world, loadedTileEntityField, manager);
			new LoadedEntityList<TileEntity>(event.world, loadedEntityField, manager);
		} catch (Exception e) {
			Log.severe("Failed to initialise tile threading for world " + event.world.getWorldInfo().getWorldName(), e);
		}
	}

	@ForgeSubscribe
	public void onWorldUnload(WorldEvent.Unload event) {
		try {
			Field loadedTileEntityField = World.class.getDeclaredField(loadedTileEntityFieldName);
			Object loadedTileEntityList = loadedTileEntityField.get(event.world);
			if (loadedTileEntityList instanceof overrideList) {
				((overrideList) loadedTileEntityList).unload();
			} else {
				Log.severe("Looks like another mod broke threading for world, probably a long time ago: " + event.world.getWorldInfo().getWorldName());
			}
		} catch (Exception e) {
			Log.severe("Probable memory leak: Failed to unload tile threading for world " + event.world.getWorldInfo().getWorldName(), e);
		}
	}
}
