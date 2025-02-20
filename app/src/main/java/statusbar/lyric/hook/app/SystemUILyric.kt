/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/577fkj/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by 577fkj.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/577fkj/StatusBarLyric/blob/main/LICENSE>.
 */

package statusbar.lyric.hook.app


import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import cn.lyric.getter.api.data.DataType
import cn.lyric.getter.api.data.LyricData
import cn.lyric.getter.api.tools.Tools.base64ToDrawable
import cn.lyric.getter.api.tools.Tools.receptionLyric
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XC_MethodHook
import statusbar.lyric.BuildConfig
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP.config
import statusbar.lyric.hook.BaseHook
import statusbar.lyric.tools.LogTools.log
import statusbar.lyric.tools.Tools.goMainThread
import statusbar.lyric.tools.Tools.isLandscape
import statusbar.lyric.tools.Tools.isNot
import statusbar.lyric.tools.Tools.isNotNull
import statusbar.lyric.tools.Tools.isTargetView
import statusbar.lyric.tools.Tools.regexReplace
import statusbar.lyric.tools.ViewTools
import statusbar.lyric.tools.ViewTools.hideView
import statusbar.lyric.tools.ViewTools.iconColorAnima
import statusbar.lyric.tools.ViewTools.showView
import statusbar.lyric.tools.ViewTools.textColorAnima
import statusbar.lyric.view.EdgeTransparentView
import statusbar.lyric.view.LyricSwitchView
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt


class SystemUILyric : BaseHook() {

    private var isScreenLock: Boolean = false
    private lateinit var hook: XC_MethodHook.Unhook
    private var lastColor: Int = 0
    private var lastLyric: String = ""
    private var lastBase64Icon: String = ""
    private var iconSwitch: Boolean = true
    private var isShow: Boolean = false
    val context: Context by lazy { AndroidAppHelper.currentApplication() }

    private val displayMetrics: DisplayMetrics by lazy { context.resources.displayMetrics }

    private val displayWidth: Int by lazy { displayMetrics.widthPixels }
    private val displayHeight: Int by lazy { displayMetrics.heightPixels }


    private lateinit var clockView: TextView
    private lateinit var targetView: ViewGroup
    private lateinit var mNotificationIconArea: View
    private lateinit var mCarrierLabel: View
    private val lyricView: LyricSwitchView by lazy {
        LyricSwitchView(context).apply {
            setTypeface(clockView.typeface)
            layoutParams = clockView.layoutParams
            setSingleLine(true)
            setMaxLines(1)
        }
    }

    private val iconView: ImageView by lazy { ImageView(context) }
    private val lyricLayout: LinearLayout by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            addView(iconView)
            addView(if (config.lyricBlurredEdges) {
                EdgeTransparentView(context, config.lyricBlurredEdgesRadius.toFloat()).apply {
                    addView(lyricView)
                }
            } else {
                lyricView
            })
            hideView()
        }
    }


    //////////////////////////////Hook//////////////////////////////////////
    @SuppressLint("DiscouragedApi")
    override fun init() {
        "Init Hook".log()
        loadClassOrNull(config.textViewClassName).isNotNull {
            hook = TextView::class.java.methodFinder().filterByName("setText").first().createHook {
                after { hookParam ->
                    (hookParam.thisObject as View).isTargetView { view ->
                        "Lyric Init".log()
                        clockView = view
                        targetView = (clockView.parent as LinearLayout).apply {
                            gravity = Gravity.CENTER
                        }
                        lyricInit()
                        hook.unhook()
                    }
                }
            }
            if (config.limitVisibilityChange) {
                moduleRes.getString(R.string.LimitVisibilityChange).log()
                View::class.java.methodFinder().filterByName("setVisibility").first().createHook {
                    before { hookParam ->
                        if (isShow) {
                            if (hookParam.args[0] == View.VISIBLE) {
                                (hookParam.thisObject as View).isTargetView {
                                    hookParam.args[0] = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        }
        when (config.lyricColorScheme) {
            0 -> {
                loadClassOrNull("com.android.systemui.statusbar.phone.DarkIconDispatcherImpl").isNotNull {
                    it.methodFinder().filterByName("applyDarkIntensity").first().createHook {
                        after { hookParam ->
                            if (!(this@SystemUILyric::clockView.isInitialized && this@SystemUILyric::targetView.isInitialized)) return@after
                            hookParam.thisObject.objectHelper {
                                val mIconTint = getObjectOrNullAs<Int>("mIconTint") ?: Color.BLACK
                                changeColor(mIconTint)
                            }

                        }
                    }
                }
            }

            1 -> {
                loadClassOrNull("com.android.systemui.statusbar.phone.NotificationIconAreaController").isNotNull {
                    it.methodFinder().filterByName("onDarkChanged").filterByParamCount(3).first().createHook {
                        after { hookParam ->
                            if (!(this@SystemUILyric::clockView.isInitialized && this@SystemUILyric::targetView.isInitialized)) return@after
                            val isDark = (hookParam.args[1] as Float) == 1f
                            changeColor(if (isDark) Color.BLACK else Color.WHITE)
                        }
                    }
                }
            }

            else -> {}
        }

        if (config.hideNotificationIcon) {
            loadClassOrNull("com.android.systemui.statusbar.phone.NotificationIconAreaController").isNotNull {
                moduleRes.getString(R.string.HideNotificationIcon).log()
                it.methodFinder().filterByName("initializeNotificationAreaViews").first().createHook {
                    after { hookParam ->
                        hookParam.thisObject.objectHelper {
                            mNotificationIconArea = this.getObjectOrNullAs<View>("mNotificationIconArea")!!
                        }
                    }
                }
            }
        }
        if (config.hideCarrier) {
            moduleRes.getString(R.string.HideCarrier).log()
            loadClassOrNull("com.android.systemui.statusbar.phone.KeyguardStatusBarView").isNotNull {
                it.methodFinder().filterByName("onFinishInflate").first().createHook {
                    after { hookParam ->
                        val frameLayout = hookParam.thisObject as RelativeLayout
                        mCarrierLabel = frameLayout.findViewById(context.resources.getIdentifier("keyguard_carrier_text", "id", context.packageName))
                    }
                }
            }
        }
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag", "MissingPermission")
    private fun lyricInit() {
        goMainThread(1) {
            if (config.viewIndex == 0) {
                targetView.addView(lyricLayout, 0)
            } else {
                targetView.addView(lyricLayout)
            }
        }
        receptionLyric(context, BuildConfig.API_VERSION) {
            if (!(this::clockView.isInitialized && this::targetView.isInitialized)) return@receptionLyric
            if (it.type == DataType.UPDATE) {
                val lyric = it.lyric.regexReplace(config.regexReplace, "")
                if (lyric.isNotEmpty()) {
                    changeLyric(lyric)
                    changeIcon(it)
                }
            } else if (it.type == DataType.STOP) {
                hideLyric()
            }
            it.log()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(UpdateConfig(), IntentFilter("updateConfig"), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(UpdateConfig(), IntentFilter("updateConfig"))
        }
        if (config.hideLyricWhenLockScreen) {
            val screenLockFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(ScreenLockReceiver(), screenLockFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(ScreenLockReceiver(), screenLockFilter)
            }

        }
        changeConfig(1)
    }

    private fun changeLyric(lyric: String) {
        if (lastLyric == lyric || isScreenLock) return
        lastLyric = lyric
        isShow = true
        "lyric:$lyric".log()
        goMainThread {
            lyricLayout.showView()
            if (config.hideTime) clockView.hideView()
            if (this::mNotificationIconArea.isInitialized && config.hideNotificationIcon) mNotificationIconArea.hideView()
            if (this::mCarrierLabel.isInitialized && config.hideCarrier) mCarrierLabel.hideView()
            lyricView.apply {
                if (config.animation == "Random") {
                    val effect = arrayListOf("Top", "Bottom", "Start", "End").random()
                    inAnimation = ViewTools.switchViewInAnima(effect)
                    outAnimation = ViewTools.switchViewOutAnima(effect)
                }
                width = getLyricWidth(paint, lyric)
                if (config.dynamicLyricSpeed) {
                    val theoreticalWidth = min(paint.measureText(lyric).toInt(), targetView.width)
                    val i = theoreticalWidth - width
                    if (i > 0) {
                        val proportion = i * 1.0 / displayWidth
                        val speed = 15 * proportion + 0.5
                        speed.log()
                        setSpeed(speed.toFloat())
                    }
                }
                setText(lyric)
            }
        }
    }

    private fun changeIcon(it: LyricData) {
        if (!iconSwitch) return
        val customIcon = it.customIcon && it.base64Icon.isNotEmpty()
        goMainThread {
            val bitmap = if (customIcon) {
                if (it.base64Icon == lastBase64Icon) return@goMainThread
                lastBase64Icon = it.base64Icon
                base64ToDrawable(it.base64Icon)
            } else {
                val baseIcon = config.getDefaultIcon(it.packageName)
                if (baseIcon == lastBase64Icon) return@goMainThread
                lastBase64Icon = baseIcon
                val base64ToDrawable = base64ToDrawable(baseIcon)
                base64ToDrawable
            }
            bitmap.isNotNull {
                iconView.showView()
                iconView.setImageBitmap(it)
            }.isNot {
                iconView.hideView()
            }
            "Change Icon".log()
        }
    }

    private fun hideLyric() {
        isShow = false
        "Hide Lyric".log()
        goMainThread {
            lyricLayout.hideView()
            clockView.showView()
            if (this::mNotificationIconArea.isInitialized) mNotificationIconArea.showView()
            if (this::mCarrierLabel.isInitialized) mCarrierLabel.showView()
        }
    }

    private fun changeColor(color: Int) {
        "Change Color".log()
        if (lastColor == color) return
        goMainThread {
            if (config.lyricColor.isEmpty()) lyricView.textColorAnima(color)
            if (config.iconColor.isEmpty()) iconView.iconColorAnima(lastColor, color)
        }
        lastColor = color
    }

    private fun changeConfig(delay: Long = 0L) {
        "Change Config".log()
        config.update()
        goMainThread(delay) {
            lyricView.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SHIFT, if (config.lyricSize == 0) clockView.textSize else config.lyricSize.toFloat())
                setMargins(config.lyricStartMargins, config.lyricTopMargins, config.lyricEndMargins, config.lyricBottomMargins)
                if (config.lyricColor.isEmpty()) {
                    textColorAnima(clockView.currentTextColor)
                } else {
                    textColorAnima(Color.parseColor(config.lyricColor))
                }
                setLetterSpacings(config.lyricLetterSpacing / 100f)
                strokeWidth(config.lyricStrokeWidth / 100f)
                if (!config.dynamicLyricSpeed) setSpeed(config.lyricSpeed.toFloat())
                if (config.lyricBackgroundRadius != 0) {
                    setBackgroundColor(Color.TRANSPARENT)
                    background = GradientDrawable().apply {
                        cornerRadius = config.lyricBackgroundRadius.toFloat()
                        setColor(Color.parseColor(config.lyricBackgroundColor))
                    }
                } else {
                    setBackgroundColor(Color.parseColor(config.lyricBackgroundColor))
                }
                val animation = config.animation
                if (animation != "Random") {
                    inAnimation = ViewTools.switchViewInAnima(animation)
                    outAnimation = ViewTools.switchViewOutAnima(animation)
                }
                runCatching {
                    val file = File("${context.filesDir.path}/font")
                    if (file.exists() && file.canRead()) {
                        setTypeface(Typeface.createFromFile(file))
                    }
                }
            }
            if (!config.iconSwitch) {
                iconView.hideView()
                iconSwitch = false
            } else {
                iconView.showView()
                iconSwitch = true
                iconView.apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply { setMargins(config.iconStartMargins, config.iconTopMargins, 0, config.iconBottomMargins) }.apply {
                        if (config.iconSize == 0) {
                            width = clockView.height / 2
                            height = clockView.height / 2
                        } else {
                            width = config.iconSize
                            height = config.iconSize
                        }
                    }
                    if (config.iconColor.isEmpty()) {
                        iconColorAnima(lastColor, clockView.currentTextColor)
                    } else {
                        iconColorAnima(lastColor, Color.parseColor(config.iconColor))
                    }
                }
            }
            if (this::mNotificationIconArea.isInitialized) if (config.hideNotificationIcon) mNotificationIconArea.hideView() else mNotificationIconArea.showView()
        }
    }

    private fun getLyricWidth(paint: Paint, text: String): Int {
        "Get Lyric Width".log()
        return if (config.lyricWidth == 0) {
            min(paint.measureText(text).toInt(), targetView.width)
        } else {
            if (config.fixedLyricWidth) {
                scaleWidth()
            } else {
                min(paint.measureText(text).toInt(), scaleWidth())
            }
        }
    }

    private fun scaleWidth(): Int {
        "Scale Width".log()
        return (config.lyricWidth / 100.0 * if (context.isLandscape()) {
            displayHeight
        } else {
            displayWidth
        }).roundToInt()
    }

    override val name: String get() = this::class.java.simpleName


    inner class UpdateConfig : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "normal" -> {
                    if (!(this@SystemUILyric::clockView.isInitialized && this@SystemUILyric::targetView.isInitialized)) return
                    changeConfig()
                }

                "change_font" -> {}
                "reset_font" -> {}
            }
        }

    }

    inner class ScreenLockReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isScreenLock = intent.action == Intent.ACTION_SCREEN_OFF
            if (isScreenLock) {
                hideLyric()
            }
        }

    }
}
