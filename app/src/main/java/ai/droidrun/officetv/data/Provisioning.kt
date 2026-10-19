package ai.droidrun.officetv.data

/** Validate before touching data; report success only after the mutation completes. */
fun provisionSecret(
    value: String?,
    clear: Boolean,
    validate: (String?) -> Boolean,
    beforeChange: () -> Unit = {},
    store: (String) -> Unit,
    remove: () -> Unit,
): String {
    if (!clear && !validate(value)) return "invalid"
    beforeChange()
    if (clear) remove() else store(requireNotNull(value))
    return if (clear) "cleared" else "stored"
}
