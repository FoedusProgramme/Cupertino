package uk.akane.cupertinodemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

class ItemAdapter(
    private val context: Context
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val containerView: ShapeableImageView = view.findViewById(R.id.container)
        val dividerView: MaterialDivider = view.findViewById(R.id.divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(
            R.layout.menu_item,
            parent,
            false
        ))

    override fun getItemCount(): Int = 128

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.containerView.shapeAppearanceModel =
            ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(
                    if (position == 0) 12f.dpToPx(context) else 0f
                )
                .setTopRightCornerSize(
                    if (position == 0) 12f.dpToPx(context) else 0f
                )
                .setBottomLeftCornerSize(
                    if (position == itemCount - 1) 12f.dpToPx(context) else 0f
                )
                .setBottomRightCornerSize(
                    if (position == itemCount - 1) 12f.dpToPx(context) else 0f
                )
                .build()
        holder.dividerView.visibility =
            if (position == itemCount - 1) View.GONE else View.VISIBLE
    }
}