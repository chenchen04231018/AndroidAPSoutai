package info.nightscout.androidaps.plugins.pump.combo

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.combo.R
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyScripter
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ComboPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var ruffyScripter: RuffyScripter

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is PumpEnactResultObject) {
                it.rh = rh
            }
        }
    }

    private lateinit var comboPlugin: ComboPlugin

    @Before
    fun prepareMocks() {
        `when`(rh.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump")
        `when`(rh.gs(R.string.combo_pump_unsupported_operation)).thenReturn("Requested operation not supported by pump")
        comboPlugin = ComboPlugin(injector, aapsLogger, RxBus(aapsSchedulers, aapsLogger), rh, profileFunction, sp, commandQueue, pumpSync, dateUtil, ruffyScripter)
    }

    @Test
    fun invalidBasalRateOnComboPumpShouldLimitLoopInvocation() {
        comboPlugin.setPluginEnabled(PluginType.PUMP, true)
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false)
        var c = Constraint(true)
        c = comboPlugin.isLoopInvocationAllowed(c)
        Assert.assertEquals("Combo: No valid basal rate read from pump", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
        comboPlugin.setPluginEnabled(PluginType.PUMP, false)
    }

    @Test
    fun `generate bolus ID from timestamp and amount`() {
        val now = System.currentTimeMillis()
        val pumpTimestamp = now - now % 1000
        // same timestamp, different bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.1, true)),
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true))
        )
        // different timestamp, same bolus leads to different fake timestamp
        Assert.assertNotEquals(
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp, 0.3, true)),
            comboPlugin.generatePumpBolusId(Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        )
        // generated timestamp has second-precision
        val bolus = Bolus(pumpTimestamp, 0.2, true)
        val calculatedTimestamp = comboPlugin.generatePumpBolusId(bolus)
        Assert.assertEquals(calculatedTimestamp, calculatedTimestamp - calculatedTimestamp % 1000)
    }
}