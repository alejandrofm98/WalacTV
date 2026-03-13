package com.example.walactv

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class GuideFragment : Fragment() {

    private lateinit var groupsContainer: LinearLayout
    private lateinit var channelListView: ListView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroDescriptionView: TextView

    private val allChannels: List<CatalogItem>
        get() = CatalogMemory.searchableItems.filter { it.kind == ContentKind.CHANNEL }

    private var selectedGroup: String = ALL_CHANNELS_GROUP
    private var visibleChannels: List<CatalogItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupsContainer = view.findViewById(R.id.guide_groups_container)
        channelListView = view.findViewById(R.id.guide_channel_list)
        heroLogoView = view.findViewById(R.id.guide_hero_logo)
        heroTitleView = view.findViewById(R.id.guide_hero_title)
        heroMetaView = view.findViewById(R.id.guide_hero_meta)
        heroDescriptionView = view.findViewById(R.id.guide_hero_description)

        renderGroups()
        applyGroup(ALL_CHANNELS_GROUP)

        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else {
                false
            }
        }
    }

    private fun renderGroups() {
        groupsContainer.removeAllViews()

        val groups = buildList {
            add(ALL_CHANNELS_GROUP)
            add(FAVORITES_GROUP)
            allChannels.map(CatalogItem::group)
                .distinct()
                .sorted()
                .forEach(::add)
        }

        groups.forEach { group ->
            val itemView = layoutInflater.inflate(R.layout.item_guide_group, groupsContainer, false) as TextView
            itemView.text = group
            itemView.setOnClickListener { applyGroup(group) }
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.setBackgroundResource(R.drawable.guide_group_selected_bg)
                    applyGroup(group)
                } else if (selectedGroup != group) {
                    view.background = null
                }
            }
            if (group == selectedGroup) {
                itemView.setBackgroundResource(R.drawable.guide_group_selected_bg)
            }
            groupsContainer.addView(itemView)
        }
    }

    private fun applyGroup(group: String) {
        selectedGroup = group
        visibleChannels = when (group) {
            ALL_CHANNELS_GROUP -> allChannels
            FAVORITES_GROUP -> {
                val favorites = ChannelStateStore(requireContext()).favoriteIds()
                allChannels.filter { favorites.contains(it.stableId) }
            }
            else -> allChannels.filter { it.group == group }
        }

        channelListView.adapter = GuideChannelAdapter(visibleChannels)
        if (visibleChannels.isNotEmpty()) {
            bindHero(visibleChannels.first())
        }

        channelListView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                visibleChannels.getOrNull(position)?.let(::bindHero)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        channelListView.setOnItemClickListener { _, _, position, _ ->
            val item = visibleChannels.getOrNull(position) ?: return@setOnItemClickListener
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(KEY_CHANNEL_ID, item.stableId) },
            )
            parentFragmentManager.popBackStack()
        }
    }

    private fun bindHero(item: CatalogItem) {
        heroTitleView.text = if (item.channelNumber != null) {
            getString(R.string.channel_card_title, item.channelNumber, item.title)
        } else {
            item.title
        }
        heroMetaView.text = item.group
        heroDescriptionView.text = item.subtitle.ifBlank { getString(R.string.guide_now_playing_placeholder) }

        if (item.imageUrl.isBlank()) {
            heroLogoView.setImageDrawable(null)
            return
        }

        Glide.with(this)
            .asBitmap()
            .load(item.imageUrl)
            .into(object : CustomTarget<android.graphics.Bitmap>() {
                override fun onResourceReady(resource: android.graphics.Bitmap, transition: Transition<in android.graphics.Bitmap>?) {
                    heroLogoView.setImageDrawable(BitmapDrawable(resources, resource))
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    heroLogoView.setImageDrawable(null)
                }
            })
    }

    private inner class GuideChannelAdapter(
        private val items: List<CatalogItem>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_guide_channel, parent, false)
            val item = items[position]

            val numberView = view.findViewById<TextView>(R.id.guide_channel_number)
            val titleView = view.findViewById<TextView>(R.id.guide_channel_title)
            val subtitleView = view.findViewById<TextView>(R.id.guide_channel_subtitle)
            val logoView = view.findViewById<ImageView>(R.id.guide_channel_logo)

            numberView.text = item.channelNumber?.toString().orEmpty()
            titleView.text = item.title
            subtitleView.text = item.subtitle.ifBlank { item.group }

            if (item.imageUrl.isBlank()) {
                logoView.setImageResource(R.drawable.ic_guide_tv)
            } else {
                Glide.with(view)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_guide_tv)
                    .into(logoView)
            }

            view.setBackgroundColor(
                if (position % 2 == 0) {
                    ContextCompat.getColor(requireContext(), R.color.guide_row_dark)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.guide_row_darker)
                },
            )
            return view
        }
    }

    companion object {
        const val REQUEST_KEY = "guide_request"
        const val KEY_CHANNEL_ID = "channel_id"

        private const val ALL_CHANNELS_GROUP = "Todos los canales"
        private const val FAVORITES_GROUP = "Favoritos"
    }
}
