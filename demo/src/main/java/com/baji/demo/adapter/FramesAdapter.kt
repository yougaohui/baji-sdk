package com.baji.demo.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.baji.demo.R
import com.bumptech.glide.Glide

/**
 * 视频帧缩略图适配器
 */
class FramesAdapter : RecyclerView.Adapter<FramesAdapter.ViewHolder>() {
    private val list = mutableListOf<String>()
    private var mWidth = 35 // 默认宽度（dp），会在设置时转换为px

    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.frames_item_layout, parent, false)
    ) {
        val mIv: ImageView = itemView.findViewById(R.id.mIv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < list.size) {
            Glide.with(holder.itemView.context)
                .load(list[position])
                .into(holder.mIv)
            
            // 设置 ImageView 的宽度
            val imageLayoutParams = holder.mIv.layoutParams
            imageLayoutParams.width = mWidth
            holder.mIv.layoutParams = imageLayoutParams
            
            // 设置 itemView 的宽度，确保每个 item 的宽度一致
            val itemLayoutParams = holder.itemView.layoutParams
            itemLayoutParams.width = mWidth
            holder.itemView.layoutParams = itemLayoutParams
            
            Log.d("FramesAdapter", "onBindViewHolder: position=$position, total=${itemCount}, itemWidth=$mWidth")
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    /**
     * 更新列表
     */
    fun updateList(frameList: List<String>) {
        list.clear()
        list.addAll(frameList)
        notifyDataSetChanged()
    }

    /**
     * 更新单个项
     */
    fun updateItem(position: Int, outfile: String) {
        if (position >= 0 && position < list.size) {
            list[position] = outfile
            notifyItemChanged(position)
        }
    }

    /**
     * 设置项宽度（像素）
     */
    fun setItemWidth(width: Int) {
        mWidth = width
    }
}
