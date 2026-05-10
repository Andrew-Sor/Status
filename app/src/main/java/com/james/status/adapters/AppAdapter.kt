/*
 *    Copyright 2019 James Fenn
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.james.status.adapters

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.james.status.R
import com.james.status.data.AppPreferenceData
import com.james.status.dialogs.preference.AppPreferenceDialog
import com.james.status.utils.StringUtils
import com.james.status.views.CustomImageView

class AppAdapter(private val context: Context, private val apps: MutableList<AppPreferenceData>) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private val packageManager: PackageManager = context.packageManager

    init {
        apps.sortWith(Comparator { lhs, rhs ->
            val label1 = lhs.getLabel(this@AppAdapter.context)
            val label2 = rhs.getLabel(this@AppAdapter.context)

            if (label1 != null && label2 != null)
                label1.compareTo(label2, ignoreCase = true)
            else 0
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_app_card, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getApp(position) ?: return

        holder.name.text = app.getLabel(holder.name.context)
        holder.packageName.text = app.name

        holder.icon.setImageDrawable(ColorDrawable(Color.TRANSPARENT))

        // Load icon in background thread instead of using afollestad.async
        Thread {
            var drawable: Drawable? = null
            getApp(holder.adapterPosition)?.let {
                try {
                    drawable = packageManager.getApplicationIcon(it.packageName)
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
            }

            val finalDrawable = drawable
            Handler(Looper.getMainLooper()).post {
                if (finalDrawable != null) {
                    holder.icon.setImageDrawable(finalDrawable)
                }
            }
        }.start()

        holder.itemView.setOnClickListener { v ->
            getApp(holder.adapterPosition)?.let {
                AppPreferenceDialog(v.context, it).show()
            }
        }
    }

    private fun getApp(position: Int): AppPreferenceData? {
        return with(apps) {
            if (position < 0 || position >= size)
            null
            else this[position]
        }
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    fun filter(string: String?) {
        if (string == null || string.isEmpty()) {
            apps.sortWith(Comparator { lhs, rhs ->
                val label1 = lhs.getLabel(this@AppAdapter.context)
                val label2 = rhs.getLabel(this@AppAdapter.context)

                if (label1 != null && label2 != null)
                   label1.compareTo(label2, ignoreCase = true)
                else 0
            })
        } else {
            apps.sortWith(Comparator { lhs, rhs ->
                val label1 = lhs.getLabel(this@AppAdapter.context)
                val label2 = rhs.getLabel(this@AppAdapter.context)

                when {
                    label1 != null && label2 != null ->
                        when {
                            label1.contains(string, ignoreCase = true) && label2.contains(string, ignoreCase = true) -> label1.compareTo(label2, ignoreCase = true)
                            label1.contains(string, ignoreCase = true) -> -1
                            label2.contains(string, ignoreCase = true) -> 1
                            else -> label1.compareTo(label2, ignoreCase = true)
                        }
                    else -> 0
                }
            })
        }
        notifyDataSetChanged()
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.title)
        val packageName: TextView = v.findViewById(R.id.subtitle)
        val icon: CustomImageView = v.findViewById(R.id.icon)
    }
}