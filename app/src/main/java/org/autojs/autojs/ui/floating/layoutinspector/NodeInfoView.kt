package org.autojs.autojs.ui.floating.layoutinspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.NumberUtils.toElegantlyDoubleString
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.opencv.core.Point
import java.lang.reflect.Field
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class NodeInfoView : RecyclerView {

    // TODO by 抠脚本人 on Jul 12, 2023.
    //  ! 调整数据结构, 对话框关闭后根据已勾选的属性, 生成选择器
    private val data = Array(FIELDS.size + 1) { Array(2) { "" } }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    init {
        initData()
        adapter = Adapter()
        layoutManager = LinearLayoutManager(context)
        addItemDecoration(
            HorizontalDividerItemDecoration.Builder(context)
                .color(context.getColor(R.color.layout_node_info_view_decoration_line))
                .size(context.resources.getInteger(R.integer.layout_node_info_view_decoration_line))
                .build()
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setNodeInfo(nodeInfo: NodeInfo) {
        for (i in FIELDS.indices) {
            try {
                data[i + 1][1] = when (val value = FIELDS[i].get(nodeInfo)) {
                    is List<*> -> {
                        when (FIELDS[i].name) {
                            "actionNames" -> value.joinToString("\n") {
                                it.toString().replace("^ACTION_".toRegex(), "")
                            }
                            else -> value.joinToString("\n")
                        }
                    }
                    else -> {
                        when (FIELDS[i].name) {
                            "bounds" -> (value as? Rect)?.let { "[ ${it.left}, ${it.top}, ${it.right}, ${it.bottom} ]" } ?: value?.toString() ?: ""
                            "center" -> (value as? Point)?.let { "[ ${toElegantlyDoubleString(it.x)}, ${toElegantlyDoubleString(it.y)} ]" } ?: value?.toString() ?: ""
                            else -> value?.toString() ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        adapter!!.notifyDataSetChanged()
    }

    private fun initData() {
        data[0][0] = resources.getString(R.string.text_attribute)
        data[0][1] = resources.getString(R.string.text_value)
        for (i in 1 until data.size) {
            data[i][0] = FIELD_NAMES[i - 1]
            data[i][1] = ""
        }
    }

    fun getCheckedDate(): Array<String> {
        // TODO by 抠脚本人 on Jul 12, 2023.
        //  ! 数据增加 checked 属性, 区分已选中项目
        val checkedArr = data.filter { it[0] == "id" || it[0] == "text" }
        return Array(checkedArr.size) {
            dataToFx(checkedArr[it])
        }
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        val mViewTypeHeader = 0
        val mViewTypeItem = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            mViewTypeHeader -> R.layout.node_info_view_header
            else -> R.layout.node_info_view_item
        }.let { ViewHolder(LayoutInflater.from(parent.context).inflate(it, parent, false)) }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.apply {
                data[position].let {
                    // attrChecked.isChecked = false
                    attrName.text = it[0]
                    attrValue.text = it[1]
                }
            }
        }

        override fun getItemCount(): Int = data.size

        override fun getItemViewType(position: Int): Int = if (position == 0) mViewTypeHeader else mViewTypeItem

    }

    internal inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // val attrChecked: CheckBox = itemView.findViewById(R.id.generate)
        val attrName: TextView = itemView.findViewById(R.id.name)
        val attrValue: TextView = itemView.findViewById(R.id.value)

        init {
            itemView.setOnClickListener {
                bindingAdapterPosition.takeIf { it in 1.until(data.size) }?.let { i ->
                    ClipboardUtils.setClip(context, dataToFx(this@NodeInfoView.data[i]))
                    ViewUtils.showSnack(this@NodeInfoView, R.string.text_already_copied_to_clip)
                }
            }
        }

    }

    private fun dataToFx(data: Array<String>): String {
        val attr = data[0]
        var value = data[1]
        when (attr) {
            "className" -> value = value.replace("^android\\.widget\\.".toRegex(), "")
            "actionNames" -> return "action(${value.split("\n").joinToString(", ") { "'$it'" }})"
            "bounds" -> return "$attr(${value.replace("[^\\d,]".toRegex(), "").replace(",", ", ")})"
            "center" -> {
                val (x, y) = value.split(Regex(",\\s*")).map {
                    it.replace(Regex("[^\\d.]+"), "").toDouble()
                }
                var result = "centerX(%X%).centerY(%Y%)"

                result = if (x == x.toLong().toDouble()) {
                    result.replace("%X%", x.toLong().toString())
                } else {
                    result.replace("%X%", "${toElegantlyDoubleString(floor(x))}, ${toElegantlyDoubleString(ceil(x))}")
                }

                result = if (y == y.toLong().toDouble()) {
                    result.replace("%Y%", y.toLong().toString())
                } else {
                    result.replace("%Y%", "${toElegantlyDoubleString(floor(y))}, ${toElegantlyDoubleString(ceil(y))}")
                }

                return result
            }
        }
        return when (NodeInfo::class.java.getDeclaredField(attr).type) {
            java.lang.String::class.java -> "$attr('$value')"
            else -> "$attr($value)"
        }
    }

    companion object {

        private val FIELD_NAMES = arrayOf(
            // Common
            "packageName", "id", "fullId", "idHex",
            "desc", "text",
            "bounds", "center", "className",
            "clickable", "longClickable", "scrollable",
            "indexInParent", "childCount", "depth",

            // Regular
            "checked", "enabled", "editable", "focusable", "checkable",
            "selected", "dismissable", "visibleToUser",

            // Rare
            "contextClickable", "focused", "accessibilityFocused",
            "rowCount", "columnCount", "row", "column", "rowSpan", "columnSpan",
            "drawingOrder",

            // Arrays
            "actionNames",
        )

        private val FIELDS = Array<Field>(FIELD_NAMES.size) {
            NodeInfo::class.java.getDeclaredField(FIELD_NAMES[it]).apply { isAccessible = true }
        }

    }

}