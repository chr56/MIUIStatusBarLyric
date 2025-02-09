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

package statusbar.lyric.tools

import android.annotation.SuppressLint
import android.content.*
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.EzXHelper
import de.robv.android.xposed.XSharedPreferences
import statusbar.lyric.BuildConfig
import statusbar.lyric.R
import statusbar.lyric.config.XposedOwnSP
import statusbar.lyric.tools.LogTools.log
import java.io.DataOutputStream
import java.util.*
import java.util.regex.Pattern


@SuppressLint("StaticFieldLeak")
object Tools {

    private lateinit var target: TextView

    private var index: Int = 0


    fun View.isTargetView(callback: (TextView) -> Unit) {
        if (this@Tools::target.isInitialized) {
            if (this == target) {
                callback(target)
            }
        } else {
            val className = XposedOwnSP.config.textViewClassName
            val textViewID = XposedOwnSP.config.textViewID
            val parentClass = XposedOwnSP.config.parentClassName
            val parentID = XposedOwnSP.config.parentID
            if (className.isEmpty() || parentClass.isEmpty() || parentID == 0) {
                EzXHelper.moduleRes.getString(R.string.LoadClassEmpty).log()
                return
            }
            if (this is TextView) {
                if (this::class.java.name == className) {
                    if (this.id == textViewID) {
                        if (this.parent is LinearLayout) {
                            val parentView = (this.parent as LinearLayout)
                            if (parentView::class.java.name == parentClass) {
                                if (parentID == parentView.id) {
                                    if (index == XposedOwnSP.config.index) {
                                        target = this
                                        callback(this)
                                    } else {
                                        index += 1
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    fun String.regexReplace(pattern: String, newString: String): String {
        val p = Pattern.compile("(?i)$pattern")
        val m = p.matcher(this)
        return m.replaceAll(newString)
    }

    fun goMainThread(delayed: Long = 0, callback: () -> Unit): Boolean {
        return Handler(Looper.getMainLooper()).postDelayed({
            callback()
        }, delayed * 1000)
    }

    fun Context.isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun String.dispose() = this.regexReplace(" ", "").regexReplace("\n", "")

    fun getPref(key: String): XSharedPreferences? {
        return try {
            val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, key)
            if (pref.file.canRead()) pref else null
        } catch (e: Throwable) {
            e.log()
            null
        }
    }

    @SuppressLint("WorldReadableFiles")
    fun getSP(context: Context, key: String): SharedPreferences? {
        @Suppress("DEPRECATION") return context.createDeviceProtectedStorageContext().getSharedPreferences(key, Context.MODE_WORLD_READABLE)
    }


    fun shell(command: String, isSu: Boolean) {
        try {
            if (isSu) {
                val p = Runtime.getRuntime().exec("su")
                val outputStream = p.outputStream
                DataOutputStream(outputStream).apply {
                    writeBytes(command)
                    flush()
                    close()
                }
                outputStream.close()
            } else {
                Runtime.getRuntime().exec(command)
            }
        } catch (ignored: Throwable) {
        }
    }


    inline fun <T> T?.isNotNull(callback: (T) -> Unit): Boolean {
        if (this != null) {
            callback(this)
            return true
        }
        return false
    }

    inline fun Boolean.isNot(callback: () -> Unit) {
        if (!this) {
            callback()
        }
    }

    inline fun Any?.isNull(callback: () -> Unit): Boolean {
        if (this == null) {
            callback()
            return true
        }
        return false
    }

    fun Any?.isNull() = this == null

    fun Any?.isNotNull() = this != null
}
