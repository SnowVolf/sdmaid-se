package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.common.CrawlerCommon
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.ExplorerSpecGenerator
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import java.util.*
import javax.inject.Inject

@Reusable
class AOSPSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val aospLabels: AOSPLabels,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean = !deviceDetective.isAndroidTV()

    override suspend fun getSpec(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        run {
            val storageEntryLabels = aospLabels.getStorageEntryDynamic()
                ?: aospLabels.getStorageEntryStatic(lang, script)

            val storageFilter = when {
                hasApiLevel(34) -> fun(node: AccessibilityNodeInfo): Boolean {
                    if (!node.isTextView()) return false
                    return node.textMatchesAny(storageEntryLabels)
                }

                else -> fun(node: AccessibilityNodeInfo): Boolean {
                    if (!node.isTextView() || !node.idContains("android:id/title")) return false
                    return node.textMatchesAny(storageEntryLabels)
                }
            }

            val step = StepProcessor.Step(
                parentTag = tag,
                label = "Find & click 'Storage' (targets=$storageEntryLabels)",
                windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                windowEventFilter = CrawlerCommon.defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter,
                nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                nodeMapping = CrawlerCommon.clickableParent(),
                action = CrawlerCommon.defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }

        run {
            val clearCacheButtonLabels = aospLabels.getClearCacheDynamic()
                ?: aospLabels.getClearCacheStatic(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = StepProcessor.Step(
                parentTag = tag,
                label = "Find & click 'Clear Cache' (targets=$clearCacheButtonLabels)",
                windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = buttonFilter,
                action = CrawlerCommon.getDefaultClearCacheClick(pkg, tag)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AOSPSpecs): SpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()

        val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Specs")
    }

}
