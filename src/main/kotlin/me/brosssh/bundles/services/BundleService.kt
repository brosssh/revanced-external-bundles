package me.brosssh.bundles.services

import app.revanced.patcher.patch.PatchBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.repositories.BundleRepository
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.PatchRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.util.intId
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class BundleService (
    private val bundleRepository: BundleRepository
) {
    fun getBySourceUrl(sourceUrl: String) = bundleRepository.findBySourceUrl(sourceUrl)

}
