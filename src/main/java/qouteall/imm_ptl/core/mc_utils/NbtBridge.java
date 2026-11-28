package qouteall.imm_ptl.core.mc_utils;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Bridges the CompoundTag based serialization used by portals with the ValueInput and ValueOutput API.
 * It also keeps the old UUID helpers that were removed from the NBT classes.
 */
public class NbtBridge {
    /**
     * A map codec that reads or writes all entries of the current compound without any wrapper key.
     */
    public static final MapCodec<CompoundTag> ROOT_CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            CompoundTag result = new CompoundTag();
            input.entries().forEach(pair -> {
                ops.getStringValue(pair.getFirst()).result().ifPresent(
                    key -> result.put(key, ops.convertTo(NbtOps.INSTANCE, pair.getSecond()))
                );
            });
            return DataResult.success(result);
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : input.keySet()) {
                prefix.add(key, NbtOps.INSTANCE.convertTo(ops, input.get(key)));
            }
            return prefix;
        }
    };

    public static CompoundTag readRoot(ValueInput input) {
        return input.read(ROOT_CODEC).orElseGet(CompoundTag::new);
    }

    public static void writeRoot(ValueOutput output, CompoundTag tag) {
        output.store(ROOT_CODEC, tag);
    }

    public static CompoundTag saveEntity(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void loadEntity(Entity entity, CompoundTag tag) {
        HolderLookup.Provider registryAccess = entity.registryAccess();
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag));
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        int[] array = tag.getIntArray(key).orElse(null);
        if (array == null || array.length != 4) {
            return new UUID(0, 0);
        }
        return UUIDUtil.uuidFromIntArray(array);
    }

    public static void putUUID(CompoundTag tag, String key, UUID uuid) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(uuid));
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.getIntArray(key).map(a -> a.length == 4).orElse(false);
    }

    public static IntArrayTag createUUID(UUID uuid) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
    }

    public static UUID loadUUID(Tag tag) {
        if (tag instanceof IntArrayTag intArrayTag && intArrayTag.size() == 4) {
            return UUIDUtil.uuidFromIntArray(intArrayTag.getAsIntArray());
        }
        return new UUID(0, 0);
    }
}
