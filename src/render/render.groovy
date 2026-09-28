// render/render.groovy -- payload -> inline client script. Always built in,
// the RENDER section. build/assemble.py replaces the @@CLIENT@@ line with
// render/client.js, full-line comments removed, and then runs
// build/strip_client_comments.py --check on the result.
// provides: render
// requires: payload pageKind
import groovy.json.JsonOutput

if (payload) {
    payload.page = pageKind        // 'browse' | 'agent' -- picks the host node
                                   // and the wording of the card
    // JsonOutput does not escape '<'; without this a value containing
    // "</script>" would terminate the inline block. These are legal JSON
    // escapes, so the JS literal restores them unchanged.
    def safeJson = { obj ->
        JsonOutput.toJson(obj).replace('<', '\\u003c')
                              .replace('>', '\\u003e')
                              .replace('&', '\\u0026')
    }
    def dataJson = safeJson(payload)
    def textJson = safeJson(TEXT)
    // Client script. Inside the dollar-slashy string below, a dollar sign
    // followed by a name interpolates and dollar-slash is an escaped slash,
    // so the block holds exactly two interpolations (dataJson, textJson) and
    // no other dollar sign. It carries no comments: what it does is explained
    // at the top of render/client.js and in docs/DESIGN.md.
    writer.write($/
<script>
// @@CLIENT@@
</script>
/$)
}
