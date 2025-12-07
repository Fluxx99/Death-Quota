package net.deathquota.mod.util;

import com.mojang.serialization.Codec;
import net.deathquota.mod.DeathQuotaMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bridges the PersistentState API differences introduced across 1.21.x.
 * Newer versions expose {@code PersistentStateType} with codec-driven IO,
 * while older releases still rely on {@code PersistentState.Type} or the
 * factory-based {@code getOrCreate} overload. This helper resolves whichever
 * surface exists at runtime via reflection so the mod can ship a single jar
 * compatible with every 1.21.x patch.
 */
public final class PersistentStateCompat {
    private static final String NEW_TYPE_NAME = "net.minecraft.world.PersistentStateType";
    private static final String LEGACY_TYPE_NAME = "net.minecraft.world.PersistentState$Type";

    private static final MappingResolver MAPPINGS = FabricLoader.getInstance().getMappingResolver();

    private static final Class<?> NEW_TYPE_CLASS = locateNewTypeClass();
    private static final Constructor<?> NEW_TYPE_CTOR = locateNewTypeConstructor();

    private static final Class<?> LEGACY_TYPE_CLASS = locateLegacyTypeClass();
    private static final Constructor<?>[] LEGACY_TYPE_CTORS = locateLegacyConstructors();

    private static final Method GET_OR_CREATE_NEW = locateTypeMethod(NEW_TYPE_CLASS, 1);
    private static final Method GET_OR_CREATE_LEGACY = locateTypeMethod(LEGACY_TYPE_CLASS, 2);
    private static final Method GET_OR_CREATE_FACTORY = locateFactoryMethod();
    private static final FactoryOrder FACTORY_ORDER = determineFactoryOrder(GET_OR_CREATE_FACTORY);

    static {
        DeathQuotaMod.LOGGER.info(
            "PersistentStateCompat init: newTypeClass={}, legacyTypeClass={}, newCtorFound={}, legacyCtorCount={}, newMethod={}, legacyMethod={}, factoryMethod={}, factoryOrder={}",
                className(NEW_TYPE_CLASS),
                className(LEGACY_TYPE_CLASS),
                NEW_TYPE_CTOR != null,
            LEGACY_TYPE_CTORS.length,
                describeMethod(GET_OR_CREATE_NEW),
                describeMethod(GET_OR_CREATE_LEGACY),
                describeMethod(GET_OR_CREATE_FACTORY),
                FACTORY_ORDER
        );
        // Always dump method signatures to help debug
        dumpMethodSignatures();
    }

    private PersistentStateCompat() {
    }

    public static <T extends PersistentState> Object createType(String storageKey,
                                                                Supplier<T> constructor,
                                                                Function<NbtCompound, T> reader,
                                                                Codec<T> codec,
                                                                DataFixTypes fixType) {
        Object stateType = instantiateNewType(storageKey, constructor, codec, fixType);
        if (stateType != null) {
            return stateType;
        }
        return instantiateLegacyType(constructor, reader, fixType);
    }

    @SuppressWarnings("unchecked")
    public static <T extends PersistentState> T get(PersistentStateManager manager,
                                                    Object stateType,
                                                    Function<NbtCompound, T> reader,
                                                    Supplier<T> constructor,
                                                    String storageKey) {
        
        DeathQuotaMod.LOGGER.debug("PersistentStateCompat.get called: stateType={}, storageKey={}", 
            stateType != null ? stateType.getClass().getName() : "null", storageKey);
        
        if (stateType != null) {
            if (NEW_TYPE_CLASS != null && NEW_TYPE_CLASS.isInstance(stateType)) {
                DeathQuotaMod.LOGGER.debug("Using NEW_TYPE_CLASS path");
                T result = (T) invoke(manager, GET_OR_CREATE_NEW, stateType);
                if (result != null) {
                    return result;
                }
            }
            if (LEGACY_TYPE_CLASS != null && LEGACY_TYPE_CLASS.isInstance(stateType)) {
                DeathQuotaMod.LOGGER.debug("Using LEGACY_TYPE_CLASS path, invoking {} with args: [{}, {}]", 
                    GET_OR_CREATE_LEGACY.getName(), stateType.getClass().getSimpleName(), storageKey);
                T result = (T) invoke(manager, GET_OR_CREATE_LEGACY, stateType, storageKey);
                DeathQuotaMod.LOGGER.debug("Legacy method returned: {}", result);
                if (result != null) {
                    return result;
                }
                DeathQuotaMod.LOGGER.warn("PersistentStateCompat.get: legacy method returned null for key={}", storageKey);
            }
        }
        T fallback = invokeFactory(manager, reader, constructor, storageKey);
        if (fallback != null) {
            return fallback;
        }
        
        // Final fallback: manually try to load from disk or create new
        DeathQuotaMod.LOGGER.warn("PersistentStateCompat.get: All reflection methods failed, using manual fallback for key={}", storageKey);
        
        // First, try to get already cached state from manager
        try {
            Method getMethod = manager.getClass().getMethod("get", String.class);
            Object cached = getMethod.invoke(manager, storageKey);
            if (cached != null) {
                DeathQuotaMod.LOGGER.info("Found cached state in manager for key={}", storageKey);
                return (T) cached;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get cached state: {}", e.getMessage());
        }
        
        try {
            // Manual fallback: directly access the data directory
            // PersistentStateManager stores files in world/data/<key>.dat
            DeathQuotaMod.LOGGER.info("Manual fallback: Constructing file path for key={}", storageKey);
            
            // Try multiple possible field names for the directory field
            java.nio.file.Path dataDir = null;
            String[] fieldNames = {"field_17664", "field_17800", "field_17801", "directory", "dataDir", "field_29767"};
            
            for (String fieldName : fieldNames) {
                try {
                    java.lang.reflect.Field dirField = manager.getClass().getDeclaredField(fieldName);
                    dirField.setAccessible(true);
                    Object dirObj = dirField.get(manager);
                    if (dirObj instanceof java.io.File) {
                        dataDir = ((java.io.File) dirObj).toPath();
                        DeathQuotaMod.LOGGER.info("Found directory field (File): {}", fieldName);
                        break;
                    } else if (dirObj instanceof java.nio.file.Path) {
                        dataDir = (java.nio.file.Path) dirObj;
                        DeathQuotaMod.LOGGER.info("Found directory field (Path): {}", fieldName);
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                }
            }
            
            // If we couldn't find the field by name, list all fields and look for Path/File
            if (dataDir == null) {
                DeathQuotaMod.LOGGER.info("Listing all fields in PersistentStateManager:");
                for (java.lang.reflect.Field field : manager.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(manager);
                    DeathQuotaMod.LOGGER.info("  Field: {} = {} (type: {})", 
                        field.getName(), 
                        value, 
                        value != null ? value.getClass().getSimpleName() : "null");
                    
                    // Check for Path first (1.21.5+), then File (older versions)
                    if (value instanceof java.nio.file.Path) {
                        java.nio.file.Path path = (java.nio.file.Path) value;
                        if (java.nio.file.Files.isDirectory(path)) {
                            dataDir = path;
                            DeathQuotaMod.LOGGER.info("Using Path directory from field: {}", field.getName());
                            break;
                        }
                    } else if (value instanceof java.io.File && ((java.io.File) value).isDirectory()) {
                        dataDir = ((java.io.File) value).toPath();
                        DeathQuotaMod.LOGGER.info("Using File directory from field: {}", field.getName());
                        break;
                    }
                }
            }
            
            if (dataDir != null) {
                java.nio.file.Path filePath = dataDir.resolve(storageKey + ".dat");
                DeathQuotaMod.LOGGER.info("Manual fallback: File path: {}, exists: {}", filePath, java.nio.file.Files.exists(filePath));
                
                if (java.nio.file.Files.exists(filePath)) {
                    DeathQuotaMod.LOGGER.info("Manual fallback: Reading NBT from file...");
                    NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(filePath, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                    DeathQuotaMod.LOGGER.info("Manual fallback: NBT loaded, calling reader function...");
                    T loaded = reader.apply(nbt);
                    if (loaded != null) {
                        DeathQuotaMod.LOGGER.info("Successfully loaded state from NBT file");
                        loaded.markDirty(); // Mark dirty to ensure it gets saved
                        
                        // Register the loaded state with the manager so future access uses it
                        if (!registerStateWithManager(manager, storageKey, loaded)) {
                            DeathQuotaMod.LOGGER.error("Failed to register loaded state with manager!");
                        }
                        
                        DeathQuotaMod.LOGGER.info("Returning loaded state from NBT file");
                        return loaded;
                    } else {
                        DeathQuotaMod.LOGGER.warn("Manual fallback: reader.apply() returned null!");
                    }
                } else {
                    DeathQuotaMod.LOGGER.info("Manual fallback: File does not exist, will create new state");
                }
            } else {
                DeathQuotaMod.LOGGER.warn("Manual fallback: Could not find directory field in PersistentStateManager");
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.warn("Failed to manually load persistent state: {} - {}", e.getClass().getName(), e.getMessage());
            DeathQuotaMod.LOGGER.debug("Stack trace:", e);
        }
        
        // Create new instance
        T newState = constructor.get();
        if (newState != null) {
            DeathQuotaMod.LOGGER.info("Created new state for key={}", storageKey);
            newState.markDirty();
            
            // CRITICAL: Register the state with the manager's internal cache so it gets saved
            if (!registerStateWithManager(manager, storageKey, newState)) {
                DeathQuotaMod.LOGGER.error("Failed to register state with manager - state will not persist!");
            }
            
            return newState;
        }
        
        throw new IllegalStateException("No compatible PersistentState#getOrCreate overload found and manual fallback failed");
    }
    
    /**
     * Registers a PersistentState with the manager's internal cache using reflection.
     * This is necessary when we manually create state objects so they get saved when the world saves.
     */
    private static <T extends PersistentState> boolean registerStateWithManager(
            PersistentStateManager manager, String storageKey, T state) {
        try {
            // The manager stores states in a Map field
            // Common field names: storageCache, states, loadedStates, field_17 ###
            java.lang.reflect.Field cacheField = null;
            
            for (java.lang.reflect.Field field : manager.getClass().getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                // Look for a Map field that could hold PersistentState objects
                if (java.util.Map.class.isAssignableFrom(fieldType)) {
                    field.setAccessible(true);
                    Object value = field.get(manager);
                    if (value instanceof java.util.Map) {
                        cacheField = field;
                        DeathQuotaMod.LOGGER.debug("Found potential cache field: {} (type: {})",
                            field.getName(), fieldType.getName());
                        break;
                    }
                }
            }
            
            if (cacheField != null) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, PersistentState> cache = 
                    (java.util.Map<String, PersistentState>) cacheField.get(manager);
                cache.put(storageKey, state);
                DeathQuotaMod.LOGGER.info("Successfully registered state in manager cache for key={}", storageKey);
                return true;
            } else {
                DeathQuotaMod.LOGGER.warn("Could not find cache field in PersistentStateManager");
                return false;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.error("Failed to register state with manager: {} - {}", 
                e.getClass().getName(), e.getMessage());
            return false;
        }
    }

    private static <T extends PersistentState> Object instantiateNewType(String storageKey,
                                                                         Supplier<T> constructor,
                                                                         Codec<T> codec,
                                                                         DataFixTypes fixType) {
        if (NEW_TYPE_CTOR == null) {
            return null;
        }
        return instantiate(NEW_TYPE_CTOR, storageKey, constructor, codec, fixType);
    }

    private static <T extends PersistentState> Object instantiateLegacyType(Supplier<T> constructor,
                                                                            Function<NbtCompound, T> reader,
                                                                            DataFixTypes fixType) {
        if (LEGACY_TYPE_CTORS.length == 0) {
            DeathQuotaMod.LOGGER.debug("No legacy constructors available");
            return null;
        }
        for (Constructor<?> ctor : LEGACY_TYPE_CTORS) {
            Object[] args = selectLegacyCtorArgs(ctor.getParameterTypes(), constructor, reader, fixType);
            if (args == null) {
                DeathQuotaMod.LOGGER.debug("Constructor {} args selection failed", ctor);
                continue;
            }
            DeathQuotaMod.LOGGER.debug("Attempting to instantiate legacy type with constructor {}", ctor);
            Object instance = instantiate(ctor, args);
            if (instance != null) {
                DeathQuotaMod.LOGGER.debug("Successfully created legacy type instance: {}", instance.getClass().getName());
                return instance;
            }
        }
        DeathQuotaMod.LOGGER.warn("Failed to instantiate any legacy type constructor");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends PersistentState> T invokeFactory(PersistentStateManager manager,
                                                               Function<NbtCompound, T> reader,
                                                               Supplier<T> constructor,
                                                               String storageKey) {
        if (GET_OR_CREATE_FACTORY == null) {
            return null;
        }
        
        // Adapt reader to BiFunction if needed (1.21.1 uses BiFunction)
        Object readerArg = reader;
        Class<?>[] params = GET_OR_CREATE_FACTORY.getParameterTypes();
        if (params.length >= 2) {
            boolean secondIsBiFunction = BiFunction.class.isAssignableFrom(params[1]);
            boolean firstIsBiFunction = BiFunction.class.isAssignableFrom(params[0]);
            if (secondIsBiFunction || firstIsBiFunction) {
                readerArg = adaptReader(reader);
            }
        }
        
        Object[] args;
        if (FACTORY_ORDER == FactoryOrder.SUPPLIER_FUNCTION) {
            args = new Object[]{constructor, readerArg, storageKey};
        } else {
            // Default to function-first ordering; UNKNOWN will retry below if needed.
            args = new Object[]{readerArg, constructor, storageKey};
        }

        T result = (T) invoke(manager, GET_OR_CREATE_FACTORY, args);
        if (result != null || FACTORY_ORDER != FactoryOrder.UNKNOWN) {
            return result;
        }

        // Retry with the opposite order in case mappings changed unexpectedly.
        return (T) invoke(manager, GET_OR_CREATE_FACTORY, constructor, readerArg, storageKey);
    }

    private static Object instantiate(Constructor<?> ctor, Object... args) {
        if (ctor == null) {
            return null;
        }
        try {
            DeathQuotaMod.LOGGER.debug("PersistentStateCompat: instantiating {} with args {}", 
                ctor.getDeclaringClass().getSimpleName(), Arrays.toString(args));
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            DeathQuotaMod.LOGGER.warn("Failed to construct persistent state helper via {} with args {}: {}", 
                ctor.getDeclaringClass().getSimpleName(), Arrays.toString(ctor.getParameterTypes()), e.getMessage());
            return null;
        }
    }

    private static <T extends PersistentState> Object[] selectLegacyCtorArgs(Class<?>[] params,
                                                                             Supplier<T> constructor,
                                                                             Function<NbtCompound, T> reader,
                                                                             DataFixTypes fixType) {
        if (params.length < 2 || params.length > 3) {
            return null;
        }
        boolean signatureOnly = constructor == null || reader == null;
        boolean firstSupplier = Supplier.class.isAssignableFrom(params[0]);
        boolean firstFunction = Function.class.isAssignableFrom(params[0]);
        boolean secondSupplier = Supplier.class.isAssignableFrom(params[1]);
        boolean secondFunction = Function.class.isAssignableFrom(params[1]);
        boolean secondBiFunction = BiFunction.class.isAssignableFrom(params[1]);
        boolean supplierFunctionCombo = (firstSupplier && (secondFunction || secondBiFunction))
                || (firstFunction && (secondSupplier));
        if (!supplierFunctionCombo) {
            return null;
        }
        if (signatureOnly) {
            return new Object[0];
        }
        Object first = firstSupplier ? constructor : reader;
        Object second;
        if (secondBiFunction) {
            // 1.21.1 uses BiFunction<NbtCompound, RegistryWrapper.WrapperLookup, T>
            second = adaptReader(reader);
        } else if (secondFunction) {
            second = reader;
        } else {
            second = constructor;
        }
        if (params.length == 2) {
            return new Object[]{first, second};
        }
        // For 3-param constructor, handle DataFixTypes - be more lenient
        if (fixType != null && DataFixTypes.class.isAssignableFrom(params[2])) {
            return new Object[]{first, second, fixType};
        }
        // Try with null if fixType is null but constructor still expects DataFixTypes
        if (DataFixTypes.class.isAssignableFrom(params[2])) {
            return new Object[]{first, second, null};
        }
        return null;
    }

    private static <T extends PersistentState> BiFunction<NbtCompound, Object, T> adaptReader(Function<NbtCompound, T> reader) {
        return (nbt, ignored) -> reader.apply(nbt);
    }

    private static Object invoke(PersistentStateManager manager, Method method, Object... args) {
        if (method == null) {
            DeathQuotaMod.LOGGER.debug("invoke: method is null");
            return null;
        }
        try {
            DeathQuotaMod.LOGGER.debug("Invoking method {} on manager {} with {} args", 
                method.getName(), manager.getClass().getSimpleName(), args.length);
            Object result = method.invoke(manager, args);
            DeathQuotaMod.LOGGER.debug("Method {} returned: {}", method.getName(), 
                result != null ? result.getClass().getSimpleName() : "null");
            return result;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            DeathQuotaMod.LOGGER.error("Failed to invoke PersistentStateManager method {} with signature {}: {}", 
                method.getName(), Arrays.toString(method.getParameterTypes()), e.toString(), e);
            return null;
        }
    }

    private static Constructor<?> locateNewTypeConstructor() {
        if (NEW_TYPE_CLASS == null) {
            return null;
        }
        for (Constructor<?> ctor : NEW_TYPE_CLASS.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 4
                    && params[0] == String.class
                    && Supplier.class.isAssignableFrom(params[1])
                    && Codec.class.isAssignableFrom(params[2])
                    && params[3] == DataFixTypes.class) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        DeathQuotaMod.LOGGER.debug("PersistentStateType constructor signature not recognized; falling back to legacy API");
        return null;
    }

    private static Constructor<?>[] locateLegacyConstructors() {
        if (LEGACY_TYPE_CLASS == null) {
            return new Constructor[0];
        }
        Constructor<?>[] ctors = Arrays.stream(LEGACY_TYPE_CLASS.getDeclaredConstructors())
            .peek(ctor -> ctor.setAccessible(true))
            .toArray(Constructor<?>[]::new);
        logLegacyConstructors("cached", LEGACY_TYPE_CLASS);
        return ctors;
    }

    private static Method locateTypeMethod(Class<?> paramClass, int paramCount) {
        if (paramClass == null) {
            DeathQuotaMod.LOGGER.debug("locateTypeMethod: paramClass is null");
            return null;
        }
        
        // First try: look for methods with "getOrCreate" or "create" in the name
        for (Method method : PersistentStateManager.class.getMethods()) {
            String methodName = method.getName();
            boolean hasCreateInName = methodName.contains("getOrCreate") || 
                                     methodName.contains("create") ||
                                     methodName.contains("Create");
            
            if (!hasCreateInName) {
                continue;
            }
            
            Class<?>[] params = method.getParameterTypes();
            if (params.length != paramCount) {
                continue;
            }
            if (!params[0].equals(paramClass) && !params[0].isAssignableFrom(paramClass) && !paramClass.isAssignableFrom(params[0])) {
                continue;
            }
            if (paramCount == 2 && params[1] != String.class) {
                continue;
            }
            if (!PersistentState.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            DeathQuotaMod.LOGGER.info("Found type method by name: {} with params {}", method.getName(), Arrays.toString(params));
            return method;
        }
        
        // Second try: if method names are obfuscated, find by signature only
        // We need a method that takes (Type, String) and returns PersistentState
        // Collect ALL candidates, then prefer the one with higher method number
        List<Method> candidates = new ArrayList<>();
        
        DeathQuotaMod.LOGGER.debug("Looking for methods taking {} parameter(s), first param type: {}", 
            paramCount, paramClass.getSimpleName());
        
        for (Method method : PersistentStateManager.class.getMethods()) {
            String methodName = method.getName();
            
            // Skip if it's clearly just a "get" method (method_XXXXX could be either)
            if (methodName.equals("get")) {
                continue;
            }
            
            Class<?>[] params = method.getParameterTypes();
            if (params.length != paramCount) {
                continue;
            }
            
            // More lenient parameter matching - check both directions and exact match
            boolean paramMatches = params[0].equals(paramClass) || 
                                  params[0].isAssignableFrom(paramClass) || 
                                  paramClass.isAssignableFrom(params[0]);
            
            if (!paramMatches) {
                continue;
            }
            if (paramCount == 2 && params[1] != String.class) {
                continue;
            }
            if (!PersistentState.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            
            DeathQuotaMod.LOGGER.debug("Found candidate method: {} with params {}", 
                methodName, Arrays.toString(params));
            candidates.add(method);
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // If multiple candidates, prefer the one with the higher method number
        // (getOrCreate was added after get, so has higher number like method_20786 vs method_17924)
        Method best = candidates.get(0);
        for (Method method : candidates) {
            String methodName = method.getName();
            if (methodName.startsWith("method_")) {
                try {
                    int methodNum = Integer.parseInt(methodName.substring(7));
                    String bestName = best.getName();
                    if (bestName.startsWith("method_")) {
                        int bestNum = Integer.parseInt(bestName.substring(7));
                        if (methodNum > bestNum) {
                            best = method;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Keep current best if parsing fails
                }
            }
        }
        
        best.setAccessible(true);
        DeathQuotaMod.LOGGER.info("Found type method by signature (chose {} from {} candidates): {} with params {}", 
            best.getName(), candidates.size(), best.getName(), Arrays.toString(best.getParameterTypes()));
        return best;
    }

    private static Method locateFactoryMethod() {
        DeathQuotaMod.LOGGER.info("Searching for factory method with 3 parameters...");
        
        // First, log ALL 3-parameter methods
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3) {
                DeathQuotaMod.LOGGER.info("  Found 3-param method: {} with params [{}, {}, {}]",
                    method.getName(),
                    params[0].getSimpleName(),
                    params[1].getSimpleName(),
                    params[2].getSimpleName());
            }
        }
        
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 3 || !String.class.isAssignableFrom(params[2])) {
                continue;
            }
            boolean firstFunction = Function.class.isAssignableFrom(params[0]) || BiFunction.class.isAssignableFrom(params[0]);
            boolean firstSupplier = Supplier.class.isAssignableFrom(params[0]);
            boolean secondFunction = Function.class.isAssignableFrom(params[1]) || BiFunction.class.isAssignableFrom(params[1]);
            boolean secondSupplier = Supplier.class.isAssignableFrom(params[1]);
            if ((firstFunction && secondSupplier) || (firstSupplier && secondFunction)) {
                method.setAccessible(true);
                DeathQuotaMod.LOGGER.info("Found factory method: {} with params {}", method.getName(), Arrays.toString(params));
                return method;
            }
        }
        DeathQuotaMod.LOGGER.warn("No factory method found!");
        return null;
    }

    private static Class<?> locateNewTypeClass() {
        Class<?> direct = findClass(NEW_TYPE_NAME);
        if (direct != null) {
            return direct;
        }
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !PersistentState.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?> candidate = params[0];
            if (hasNewConstructorSignature(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasNewConstructorSignature(Class<?> candidate) {
        for (Constructor<?> ctor : candidate.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 4
                    && params[0] == String.class
                    && Supplier.class.isAssignableFrom(params[1])
                    && Codec.class.isAssignableFrom(params[2])
                    && params[3] == DataFixTypes.class) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> locateLegacyTypeClass() {
        Class<?> direct = findClass(LEGACY_TYPE_NAME);
        if (direct != null) {
            logLegacyConstructors("named", direct);
            return direct;
        }
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 2 || params[1] != String.class) {
                continue;
            }
            if (!PersistentState.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?> candidate = params[0];
            logLegacyConstructors("discovered", candidate);
            return candidate;
        }
        return null;
    }

    private static void logLegacyConstructors(String source, Class<?> candidate) {
        try {
            Constructor<?>[] ctors = candidate.getDeclaredConstructors();
            if (ctors.length == 0) {
                DeathQuotaMod.LOGGER.debug("PersistentStateCompat: no constructors found for {} (source={})", candidate.getName(), source);
                return;
            }
            StringBuilder builder = new StringBuilder("PersistentStateCompat constructors [source=")
                    .append(source)
                    .append(", class=")
                    .append(candidate.getName())
                    .append("]: ");
            for (Constructor<?> ctor : ctors) {
                builder.append(Arrays.toString(ctor.getParameterTypes())).append(' ');
            }
            DeathQuotaMod.LOGGER.info(builder.toString());
        } catch (Throwable t) {
            DeathQuotaMod.LOGGER.debug("PersistentStateCompat: failed to inspect constructors for {}", candidate.getName(), t);
        }
    }

    private static FactoryOrder determineFactoryOrder(Method method) {
        if (method == null) {
            return FactoryOrder.UNKNOWN;
        }
        Class<?>[] params = method.getParameterTypes();
        boolean firstIsFunction = Function.class.isAssignableFrom(params[0]) || BiFunction.class.isAssignableFrom(params[0]);
        boolean firstIsSupplier = Supplier.class.isAssignableFrom(params[0]);
        boolean secondIsFunction = Function.class.isAssignableFrom(params[1]) || BiFunction.class.isAssignableFrom(params[1]);
        boolean secondIsSupplier = Supplier.class.isAssignableFrom(params[1]);
        
        if (firstIsFunction && secondIsSupplier) {
            return FactoryOrder.FUNCTION_SUPPLIER;
        }
        if (firstIsSupplier && secondIsFunction) {
            return FactoryOrder.SUPPLIER_FUNCTION;
        }
        return FactoryOrder.UNKNOWN;
    }

    private static Class<?> findClass(String name) {
        ClassLoader loader = PersistentStateCompat.class.getClassLoader();
        String mapped = mapRuntimeName(name);
        if (mapped != null) {
            Class<?> cls = tryLoad(loader, mapped);
            if (cls != null) {
                return cls;
            }
        }
        if (!name.equals(mapped)) {
            Class<?> cls = tryLoad(loader, name);
            if (cls != null) {
                return cls;
            }
        }
        return null;
    }

    private static Class<?> tryLoad(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static String mapRuntimeName(String named) {
        try {
            String runtime = MAPPINGS.mapClassName("named", named);
            return runtime == null ? named : runtime;
        } catch (Throwable ignored) {
            return named;
        }
    }

    private static String className(Class<?> cls) {
        return cls == null ? "<missing>" : cls.getName();
    }

    private static String describeMethod(Method method) {
        if (method == null) {
            return "<missing>";
        }
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static void dumpMethodSignatures() {
        StringBuilder builder = new StringBuilder("PersistentStateManager candidate overloads: ");
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 1 || params.length > 3) {
                continue;
            }
            if (params.length == 3 && !String.class.isAssignableFrom(params[2])) {
                continue;
            }
            if (params.length == 2 && params[1] != String.class) {
                continue;
            }
            if (!PersistentState.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            builder.append('[')
                    .append(method.getName())
                    .append(Arrays.toString(params))
                    .append(']');
        }
        DeathQuotaMod.LOGGER.info(builder.toString());
        
        // Additional dump: show ALL methods taking (Type, String)
        DeathQuotaMod.LOGGER.info("All PersistentStateManager methods with (Type, String) signature:");
        for (Method method : PersistentStateManager.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && 
                params[1] == String.class && 
                PersistentState.class.isAssignableFrom(method.getReturnType())) {
                DeathQuotaMod.LOGGER.info("  - {} returns {}", 
                    method.getName() + Arrays.toString(params), 
                    method.getReturnType().getSimpleName());
            }
        }
    }

    private enum FactoryOrder {
        FUNCTION_SUPPLIER,
        SUPPLIER_FUNCTION,
        UNKNOWN
    }
}
