package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.block.DonationBoxBlock;
import com.sanhiruzu.musubi.block.LoomBlock;
import com.sanhiruzu.musubi.block.ShinbokuBlock;
import com.sanhiruzu.musubi.block.ShrineStoneBlock;
import com.sanhiruzu.musubi.block.SpinningWheelBlock;
import com.sanhiruzu.musubi.block.SpoolRackBlock;
import com.sanhiruzu.musubi.blockentity.DonationBoxBlockEntity;
import com.sanhiruzu.musubi.blockentity.LoomBlockEntity;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import com.sanhiruzu.musubi.blockentity.ShrineStoneBlockEntity;
import com.sanhiruzu.musubi.blockentity.SpinningWheelBlockEntity;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class MusubiJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SpoolRackJadeProvider.INSTANCE,     SpoolRackBlockEntity.class);
        registration.registerBlockDataProvider(ShrineStoneJadeProvider.INSTANCE,   ShrineStoneBlockEntity.class);
        registration.registerBlockDataProvider(DonationBoxJadeProvider.INSTANCE,   DonationBoxBlockEntity.class);
        registration.registerBlockDataProvider(ShinbokuJadeProvider.INSTANCE,      ShinbokuBlockEntity.class);
        registration.registerBlockDataProvider(SpinningWheelJadeProvider.INSTANCE, SpinningWheelBlockEntity.class);
        registration.registerBlockDataProvider(LoomJadeProvider.INSTANCE,          LoomBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SpoolRackJadeProvider.INSTANCE,     SpoolRackBlock.class);
        registration.registerBlockComponent(ShrineStoneJadeProvider.INSTANCE,   ShrineStoneBlock.class);
        registration.registerBlockComponent(DonationBoxJadeProvider.INSTANCE,   DonationBoxBlock.class);
        registration.registerBlockComponent(ShinbokuJadeProvider.INSTANCE,      ShinbokuBlock.class);
        registration.registerBlockComponent(SpinningWheelJadeProvider.INSTANCE, SpinningWheelBlock.class);
        registration.registerBlockComponent(LoomJadeProvider.INSTANCE,          LoomBlock.class);
    }
}
