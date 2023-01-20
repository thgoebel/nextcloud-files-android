/*
 * Nextcloud Android client application
 *
 *  @author Álvaro Brey
 *  Copyright (C) 2022 Álvaro Brey
 *  Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.client.documentscan

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.client.di.Injectable
import com.nextcloud.client.di.ViewModelFactory
import com.nextcloud.client.logger.Logger
import com.owncloud.android.R
import com.owncloud.android.databinding.ActivityDocumentScanBinding
import com.owncloud.android.ui.activity.ToolbarActivity
import com.owncloud.android.utils.theme.ViewThemeUtils
import com.zynksoftware.documentscanner.ui.DocumentScanner
import javax.inject.Inject

class DocumentScanActivity : ToolbarActivity(), Injectable {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    lateinit var binding: ActivityDocumentScanBinding

    lateinit var viewModel: DocumentScanViewModel

    private val scanPage = registerForActivityResult(ScanPageContract()) { result ->
        viewModel.onScanPageResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val folder = intent.extras?.getString(EXTRA_FOLDER)
        require(folder != null) { "Folder must be provided for upload" }

        viewModel = ViewModelProvider(this, vmFactory)[DocumentScanViewModel::class.java]
        viewModel.setUploadFolder(folder)

        setupViews()

        DocumentScanner.init(this) // TODO this should go back to AppScanActivity, it needs the lib!

        observeState()
    }

    private fun setupViews() {
        binding = ActivityDocumentScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
            viewThemeUtils.files.themeActionBar(this, it)
        }

        viewThemeUtils.material.themeFAB(binding.fab)
        binding.fab.setOnClickListener {
            viewModel.onAddPageClicked()
        }

        binding.pagesRecycler.layoutManager = GridLayoutManager(this, PAGE_COLUMNS)

        setupMenu()
    }

    private fun setupMenu() {
        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.activity_document_scan, menu)
                    menu.findItem(R.id.action_save)?.let {
                        viewThemeUtils.platform.colorToolbarMenuIcon(this@DocumentScanActivity, it)
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_save -> {
                            viewModel.onClickDone()
                            true
                        }
                        android.R.id.home -> {
                            onBackPressed()
                            true
                        }
                        else -> false
                    }
                }
            }
        )
    }

    private fun observeState() {
        viewModel.uiState.observe(this, ::handleState)
    }

    private fun handleState(state: DocumentScanViewModel.UIState) {
        logger.d(TAG, "handleState: called with $state")
        when (state) {
            is DocumentScanViewModel.UIState.BaseState -> when (state) {
                is DocumentScanViewModel.UIState.NormalState -> {
                    updateButtonsEnabled(true)
                    val pageList = state.pageList
                    updateRecycler(pageList)
                    if (state.shouldRequestScan) {
                        startPageScan()
                    }
                }
                is DocumentScanViewModel.UIState.RequestExportState -> {
                    updateButtonsEnabled(false)
                    if (state.shouldRequestExportType) {
                        showExportDialog()
                    }
                }
            }
            DocumentScanViewModel.UIState.DoneState -> {
                finish()
            }
        }
    }

    private fun showExportDialog() {
        // TODO better dialog
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.document_scan_export_dialog_title)
            .setPositiveButton(R.string.document_scan_export_dialog_pdf) { _, _ ->
                viewModel.onExportTypeSelected(DocumentScanViewModel.ExportType.PDF)
            }
            .setNeutralButton(R.string.document_scan_export_dialog_images) { _, _ ->
                viewModel.onExportTypeSelected(DocumentScanViewModel.ExportType.IMAGES)
            }
            .setNegativeButton(R.string.common_cancel) { _, _ ->
                viewModel.onExportCanceled()
            }
            .show()
    }

    private fun updateRecycler(pageList: List<String>) {
        if (binding.pagesRecycler.adapter == null) {
            binding.pagesRecycler.adapter = DocumentPageListAdapter()
        }
        (binding.pagesRecycler.adapter as? DocumentPageListAdapter)?.submitList(pageList)
    }

    private fun updateButtonsEnabled(enabled: Boolean) {
        binding.fab.isEnabled = enabled
    }

    private fun startPageScan() {
        logger.d(TAG, "startPageScan() called")
        viewModel.onScanRequestHandled()
        scanPage.launch(Unit)
    }

    companion object {
        private const val TAG = "DocumentScanActivity"
        private const val PAGE_COLUMNS = 2
        const val EXTRA_FOLDER = "extra_folder"
    }
}
