---
title: "API for Other Mods (old version)"
category: "Developers"
order: 16
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/Old-API.html"
updated: 2023-12-30
outdated: true
---

These are the documentation for the APIs in older versions. Supporting old versions is in low priority. It's recommended to use the latest version of iPortal+ and q_misc_util.

Code is in yarn mapping.

The content in this page is outdated.

### Dimension API in MC 1.20.2

The dimension API in 1.20.2 is in `q_misc_util`. It's roughly the same as the dimension API in DimLib.

### Dimension API in MC 1.20.1

#### Basic Concepts of Dimensions

Minecraft allows defining dimension types, biomes and other things via datapacks. Fabric will turn mod files in `resources` into virtual datapacks.

You can get a `DynamicRegistryManager` from a server, then you can get the dimension type registry from the registry manager, and then you can get the dimension type from the dimension type registry.

A dimension consists of:

- A dimension id (Its type is `RegistryKey<World>`. An `Identifier` can be converted to `RegistryKey<World>`)
- A dimension type. It defines the world height, skylight and other properties. (You can define your dimension types in json files. Then you can get it from the `DynamicRegistryManager`)
- A chunk generator. It does world generation of that dimension. To create the chunk generator, you probably need to access the registries to get the biomes and other things via `DynamicRegistryManager`.

A `DimensionOptions` contains a dimension type `RegistryEntry` and a chunk generator.

In Mojang mapping, `DynamicRegistryManager` is `RegistryAccess`, `RegistryKey` is `ResourceKey`, `DimensionOptions` is `WorldStem`, `GeneratorOptions` is `WorldGenSettings`, `RegistryEntry` is `Holder`, `Identifier` is `ResourceLocation`.

#### Dynamically Adding and Removing Dimensions

The Dimension API supports dynamically adding and removing dimensions when the server is running.

Add a new dimension dynamically:

```java
DynamicRegistryManager manager = MiscHelper.getServer().getRegistryManager();

// get the dimension type
RegistryEntry<DimensionType> dimensionType = manager.getEntry(
    RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier("namespace:dimension_type_id"))
).get();

// add the dimension
DimensionAPI.addDimensionDynamically(
    new Identifier("namespace:new_dimension_id"),
    new DimensionOptions(
        dimensionType,
        new CustomChunkGenerator(...)
    )
);
```

That code will add the new dimension to the server world map and send dimension sync packets to client. You should not do this during server initialization.

When the server restarts, the dynamically added dimension will vanish. To make sure that dynamically added dimensions are still present when the server restarts, you need to save the dimension configuration:

```java
RegistryKey<World> dimId = RegistryKey.of(Registry.WORLD_KEY, new Identifier("namespace:new_dimension_id"));

DimensionAPI.saveDimensionConfiguration(dimId);
```

Then it will save the configuration as a json file in the folder `q_dimension_configs` in the world saving.

Remove a dimension dynamically:

```java
RegistryKey<World> dimId = RegistryKey.of(Registry.WORLD_KEY, new Identifier("namespace:new_dimension_id"));

ServerWorld world = MiscHelper.getServer().getWorld(dimId);

DimensionAPI.removeDimensionDynamically(world);
```

That code will remove the dimension from the server world map and send sync packets to client. This will not delete the world saving (blocks, entities) of that dimension. If you re-add the dimension, the blocks and entities will still be there.

If you saved that dimension's configuration, you need to delete the configuration:

```java
DimensionAPI.deleteDimensionConfiguration(dimId);
```

**NOTE: currently the saved extra dimension configurations won't be updated by DFU. It means that if that dimension's chunk generator uses vanilla data formats and the format changes, the dimension will fail to load. The world data of that dimension won't be lost.**

#### Add dimension during server startup:

```java
DimensionAPI.serverDimensionsLoadEvent.register((generatorOptions, registryManager) -> {
    Registry<DimensionOptions> registry = registryManager.get(RegistryKeys.DIMENSION);
    
    // get the dimension type
    RegistryEntry<DimensionType> dimType = registryManager.get(Registry.DIMENSION_TYPE_KEY).getEntry(
        RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier("namespace:dimension_type_id"))
    ).orElseThrow(() -> new RuntimeException("Missing dimension type"));
        
    Identifier dimId = new Identifier("namespace:dimension_id");
    
    // get the biome registry for initializing the biome source
    Registry<Biome> biomeRegistry = registryManager.get(Registry.BIOME_KEY);
    BiomeSource biomeSource = new CustomBiomeSource(seed, biomeRegistry);
        
    // add the dimension
    DimensionAPI.addDimension(
        registry, dimId, dimType,
        new CustomChunkGenerator()
    );
});
```

### Dimension API In 1.18.2 and 1.19.2:

```java
DimensionAPI.serverDimensionsLoadEvent.register((generatorOptions, registryManager) -> {
    Registry<DimensionOptions> registry = generatorOptions.getDimensions();
    
    // get the dimension type
    RegistryEntry<DimensionType> dimType = registryManager.get(Registry.DIMENSION_TYPE_KEY).getEntry(
        RegistryKey.of(Registry.DIMENSION_TYPE_KEY, new Identifier("namespace:dimension_type_id"))
    ).orElseThrow(() -> new RuntimeException("Missing dimension type"));
        
    Identifier dimId = new Identifier("namespace:dimension_id");
    
    // get the biome registry for initializing the biome source
    Registry<Biome> biomeRegistry = registryManager.get(Registry.BIOME_KEY);
    BiomeSource biomeSource = new CustomBiomeSource(seed, biomeRegistry);
        
    // add the dimension
    DimensionAPI.addDimension(
        registry, dimId, dimType,
        new CustomChunkGenerator()
    );
    
    // mark it non-persistent so it won't be saved into level.dat. (This is not needed in 1.19)
    DimensionAPI.markDimensionNonPersistent(dimId);
});
```

### Dimension API In 1.17.1 and 1.18.1:

```java
DimensionAPI.serverDimensionsLoadEvent.register((generatorOptions, registryManager) -> {
    SimpleRegistry<DimensionOptions> registry = generatorOptions.getDimensions();
    long seed = generatorOptions.getSeed();
    
    // get the dimension type
    DimensionType dimensionType = registryManager.get(Registry.DIMENSION_TYPE_KEY)
        .get(new Identifier("namespace:dimension_type_id"));
    Validate.notNull(dimensionType);
    
    // get the biome registry for initializing the biome source
    MutableRegistry<Biome> biomeRegistry = registryManager.get(Registry.BIOME_KEY);
    BiomeSource biomeSource = new CustomBiomeSource(seed, biomeRegistry);
    
    // directly register the dimension
    Identifier dimensionId = new Identifier("namespace:dimension_id");
    DimensionAPI.addDimension(
        seed, registry, dimensionId, () -> dimensionType,
        new CustomChunkGenerator(seed, biomeSource)
    );
    
    // mark it non-persistent so it won't be saved into level.dat
    DimensionAPI.markDimensionNonPersistent(dimensionId);
});
```
