package me.brosssh.bundles.domain.models

import app.revanced.patcher.patch.Package
import app.revanced.patcher.patch.Patch as ReVancedPatch
import app.morphe.patcher.patch.Patch as MorphePatch

interface Patch {
    val name: String?
    val description: String?
    val compatiblePackages: Set<Package>?
}

class ReVancedPatchAdapter(
    val patch: ReVancedPatch<*>
) : Patch {
    override val name get() = patch.name
    override val description get() = patch.description
    override val compatiblePackages get() = patch.compatiblePackages
}

class MorphePatchAdapter(
    val patch: MorphePatch<*>
) : Patch {
    override val name get() = patch.name
    override val description get() = patch.description
    override val compatiblePackages get() = patch.compatiblePackages
}
