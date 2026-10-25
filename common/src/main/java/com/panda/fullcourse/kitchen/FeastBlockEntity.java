package com.panda.fullcourse.kitchen;

import com.panda.fullcourse.registry.ModBlockEntities;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Who a placed feast has served: each player takes one serving of it, so a feast is shared, not one player's eight meals. */
public class FeastBlockEntity extends BlockEntity {
    private final Set<UUID> served = new LinkedHashSet<>();

    public FeastBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FEAST.get(), pos, state);
    }

    public boolean served(UUID player) {
        return served.contains(player);
    }

    public void serve(UUID player) {
        if (served.add(player)) setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!served.isEmpty()) out.store("served", UUIDUtil.CODEC_LINKED_SET, served);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        served.clear();
        in.read("served", UUIDUtil.CODEC_LINKED_SET).ifPresent(served::addAll);
    }
}
