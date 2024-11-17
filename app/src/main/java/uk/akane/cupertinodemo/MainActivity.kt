package uk.akane.cupertinodemo

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.Log
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.CornerFamily

class MainActivity : AppCompatActivity() {

    /*

    private lateinit var cupertinoCustomView: CupertinoCustomView
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemAdapter: ItemAdapter
    private lateinit var titleBarLayout: FrameLayout

    private var initialTranslation = 0F

     */

    private val overlayPaint = Paint().apply {
        blendMode = BlendMode.OVERLAY
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        /*

        cupertinoCustomView = findViewById(R.id.ccv)
        recyclerView = findViewById(R.id.recyclerView)
        titleBarLayout = findViewById(R.id.titlebar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            Log.d("TAG", "insets: ${insets.top}")
            Log.d("TAG", "1st: ${titleBarLayout.paddingTop}")
            titleBarLayout.setPadding(
                titleBarLayout.paddingLeft,
                titleBarLayout.paddingTop + insets.top,
                titleBarLayout.paddingRight,
                titleBarLayout.paddingBottom
            )
            Log.d("TAG", "2st: ${titleBarLayout.paddingTop}")

            cupertinoCustomView.setLayoutParams(FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.custom_view_height) + insets.top
            ));
            cupertinoCustomView.setWeakRef(recyclerView)

            // Return CONSUMED if you don't want want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        itemAdapter = ItemAdapter(this)
        recyclerView.adapter = itemAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        titleBarLayout.doOnLayout {
            recyclerView.setPadding(
                recyclerView.paddingLeft,
                titleBarLayout.height,
                recyclerView.paddingRight,
                recyclerView.paddingBottom
            )
            initialTranslation = recyclerView.translationY
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                titleBarLayout.translationY = - recyclerView.computeVerticalScrollOffset().toFloat()
            }
        })

         */
    }
}