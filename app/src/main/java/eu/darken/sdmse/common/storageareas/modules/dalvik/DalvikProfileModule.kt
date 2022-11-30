package eu.darken.sdmse.common.storageareas.modules.dalvik

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.hasFlags
import eu.darken.sdmse.common.storageareas.modules.DataAreaModule
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class DalvikProfileModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<StorageArea> = emptySet()

    override suspend fun secondPass(firstPass: Collection<StorageArea>): Collection<StorageArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val possibleLocation = mutableSetOf<LocalPath>()

        firstPass
            .filter { it.type == StorageArea.Type.DATA }
            .filter { it.hasFlags(StorageArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache", "profiles") }
            .run { possibleLocation.addAll(this) }

        firstPass
            .filter { it.type == StorageArea.Type.DOWNLOAD_CACHE }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache", "profiles") }
            .run { possibleLocation.addAll(this) }

        return possibleLocation
            .filter { gateway.exists(it, mode = LocalGateway.Mode.ROOT) }
            .map {
                StorageArea(
                    type = StorageArea.Type.DALVIK_PROFILE,
                    path = it,
                    userHandle = userManager2.systemUser,
                )
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikProfileModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("StorageArea", "Module", "DalvikProfile")
    }
}