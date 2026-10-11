package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

internal const val MIME_HLS = "application/x-mpegURL"

/** Path segments that precede the video ID on these hosts (`/e/{id}`, `/d/{id}`, `/v/{id}`…). */
private val ID_PREFIXES = setOf("e", "d", "f", "v", "s", "embed", "file", "download")

/** The words these hosts' "deleted" pages use. */
private val GONE_MARKERS = listOf("file is no longer available", "file not found", "video not found", "file was deleted", "has been removed")

/**
 * The common shape of the "XFileSharing + JW Player" hosts: fetch the player page, unpack its
 * scripts, and read the `sources`/`links` config ([PageScraper.findStream]). They serve HLS
 * from CDNs that check the page's origin, so Referer/Origin are passed on to the player.
 *
 * @param playerUrl the page to fetch for a link, given the link's host and video ID — some
 *   hosts redirect retired domains to a live mirror here.
 */
open class JwPlayerHostResolver(
    final override val displayName: String,
    private val hosts: HostMatcher,
    private val playerUrl: (host: String, id: String, link: HttpUrl) -> String,
) : StreamResolver {

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "${displayName.lowercase()}:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        val pageUrl = StreamHttp.url(playerUrl(url.host.removePrefix("www."), id, url))
        val page = StreamHttp.get(pageUrl, mapOf("Referer" to pageUrl.rootUrl()))
        val expanded = PageScraper.expand(page.body)
        val stream = PageScraper.findStream(expanded, page.url) ?: throw notFound(page.body)
        return ResolvedStream(
            streamUrl = stream,
            title = PageScraper.cleanTitle(PageScraper.findTitle(page.body), displayName),
            posterUrl = PageScraper.findPoster(expanded),
            mimeType = if ("m3u8" in stream) MIME_HLS else null,
            requestHeaders = mapOf("Referer" to page.url.rootUrl(), "Origin" to page.url.origin()),
        )
    }

    /** `/e/{id}`, `/{id}`, `/embed-{id}.html`, `/v/{id}/name.mp4` → `{id}`. */
    internal open fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val raw = when {
            segments.isEmpty() -> return null
            segments[0] in ID_PREFIXES -> segments.getOrNull(1) ?: return null
            else -> segments[0]
        }
        return raw.removePrefix("embed-").removeSuffix(".html")
            .takeIf { id -> id.length >= 6 && id.all { it.isLetterOrDigit() } }
    }

    /** A page with neither a "gone" message nor any player at all (VidNest's deleted-file page
     * is just the site chrome) means the video is missing; a player we can't read means the
     * host changed something. */
    private fun notFound(html: String): StreamError =
        if (GONE_MARKERS.any { html.contains(it, ignoreCase = true) } ||
            (!html.contains("jwplayer", ignoreCase = true) && !JsUnpacker.isPacked(html))
        ) {
            StreamError.VideoUnavailable()
        } else {
            StreamError.Unreadable("No player sources on page")
        }
}

/** LuluStream — `lulustream.com/{id}`, `/e/{id}`, `/d/{id}`. */
object LuluStreamResolver : JwPlayerHostResolver(
    displayName = "LuluStream",
    hosts = HostMatcher(
        domains = setOf(
            "lulustream.com", "luluvdo.com", "lulu.st", "luluvid.com", "732eg54de642sa.sbs",
            "cdn1.site", "streamhihi.com", "luluvdoo.com", "d00ds.site", "lulust.com", "luluvido.com",
        ),
        pattern = Regex("""lulu(?:st(?:ream)?|vi*do*)?\.[a-z]{2,4}"""),
    ),
    playerUrl = { host, id, _ -> "https://$host/e/$id" },
)

/**
 * StreamWish — `streamwish.to/e/{id}` and the dozens of `*wish*` mirrors. Every one of them
 * serves the same files, and most old domains are dead, so links are fetched from a current
 * mirror (the same ones ResolveURL uses) regardless of the domain in the link.
 */
object StreamWishResolver : JwPlayerHostResolver(
    displayName = "StreamWish",
    hosts = HostMatcher(
        domains = setOf(
            "streamwish.com", "streamwish.to", "ajmidyad.sbs", "khadhnayad.sbs", "yadmalik.sbs",
            "hayaatieadhab.sbs", "kharabnahs.sbs", "atabkhha.sbs", "atabknha.sbs", "atabknhk.sbs",
            "atabknhs.sbs", "abkrzkr.sbs", "abkrzkz.sbs", "vidmoviesb.xyz", "cilootv.store",
            "uqloads.xyz", "tuktukcinema.store", "ankrzkz.sbs", "volvovideo.top", "ankrznm.sbs",
            "eghjrutf.sbs", "eghzrutw.sbs", "guxhag.com", "playembed.online", "egsyxurh.sbs",
            "egtpgrvh.sbs", "cinemathek.online", "trgsfjll.sbs", "fsdcmo.sbs", "hailindihg.com",
            "anime4low.sbs", "mohahhda.site", "ma2d.store", "dancima.shop", "swhoi.com", "aiavh.com",
            "gsfqzmqu.sbs", "swdyu.com", "kravaxxa.com", "katomen.store", "iplayerhls.com",
            "hlsflast.com", "4yftwvrdz7.sbs", "ghbrisk.com", "hgbazooka.com", "eb8gfmjn71.sbs",
            "cybervynx.com", "edbrdl7pab.sbs", "stbhg.click", "dhcplay.com", "gradehgplus.com",
            "tryzendm.com", "hglink.to", "dumbalag.com", "haxloppd.com", "davioad.com", "uasopt.com",
            "hgcloud.to", "hgplaycdn.com", "hglamioz.com", "niramirus.com", "playnixes.com",
            "medixiru.com", "hanerix.com", "audinifer.com", "vibuxer.com",
        ),
        // streamwish.*, embedwish.com, mwish.pro, flaswish.com, wishonly.site, swishsrv.com, …
        pattern = Regex("""[a-z]*wish[a-z]*\.(?:com|to|sbs|pro|xyz|store|top|site|online|me|shop|fun|click)"""),
    ),
    playerUrl = { host, id, _ ->
        val mirror = when (host) {
            "dhcplay.com", "hglink.to", "hgcloud.to" -> "hanerix.com"
            else -> "hgplaycdn.com"
        }
        "https://$mirror/e/$id"
    },
)

/**
 * VidHide (formerly FileLions) — `vidhide.com/v/{id}`, `/e/{id}`, `/f/{id}`… Retired domains are
 * redirected to a live mirror; live ones are fetched as-is with their original path.
 */
object VidHideResolver : JwPlayerHostResolver(
    displayName = "VidHide",
    hosts = HostMatcher(
        domains = setOf(
            "filelions.com", "filelions.to", "ajmidyadfihayh.sbs", "alhayabambi.sbs", "vidhideplus.com",
            "moflix-stream.click", "azipcdn.com", "mlions.pro", "alions.pro", "dlions.pro",
            "filelions.live", "motvy55.store", "filelions.xyz", "lumiawatch.top", "filelions.online",
            "javplaya.com", "fviplions.com", "egsyxutd.sbs", "filelions.site", "filelions.co",
            "vidhide.com", "vidhidepro.com", "vidhidevip.com", "javlion.xyz", "fdewsdc.sbs",
            "techradar.ink", "anime7u.com", "coolciima.online", "gsfomqu.sbs", "vidhidepre.com",
            "katomen.online", "vidhide.fun", "vidhidehub.com", "dhtpre.com", "6sfkrspw4u.sbs",
            "streamvid.su", "movearnpre.com", "bingezove.com", "dingtezuni.com", "dinisglows.com",
            "ryderjet.com", "e4xb5c2xnz.sbs", "smoothpre.com", "videoland.sbs", "taylorplayer.com",
            "mivalyo.com", "vidhidefast.com", "peytonepre.com", "dintezuvio.com", "callistanise.com",
            "minochinos.com", "earnvids.xyz", "lookmovie2.skin", "morencius.com",
        ),
        pattern = Regex("""vidhide[a-z]*\.[a-z]{2,6}"""),
    ),
    playerUrl = { host, id, link ->
        // Domains ResolveURL has confirmed dead; everything else is still served directly.
        val dead = setOf(
            "filelions.com", "filelions.to", "ajmidyadfihayh.sbs", "alhayabambi.sbs", "vidhideplus.com",
            "azipcdn.com", "mlions.pro", "alions.pro", "dlions.pro", "mivalyo.com", "vidhidefast.com",
            "filelions.live", "motvy55.store", "filelions.xyz", "lumiawatch.top", "filelions.online",
            "fviplions.com", "egsyxutd.sbs", "filelions.site", "filelions.co", "vidhidepre.com",
            "vidhidepro.com", "vidhidevip.com", "e4xb5c2xnz.sbs", "taylorplayer.com", "ryderjet.com",
            "techradar.ink", "anime7u.com", "coolciima.online", "gsfomqu.sbs", "bingezove.com",
            "katomen.online", "vidhide.fun", "6sfkrspw4u.sbs", "dingtezuni.com", "dinisglows.com",
            "dintezuvio.com",
        )
        val mirror = if (host in dead) "callistanise.com" else host
        val prefix = link.pathSegments.firstOrNull()?.takeIf { it in ID_PREFIXES } ?: "v"
        "https://$mirror/$prefix/$id"
    },
)

/** VidNest — `vidnest.io/{id}`, `/e/{id}`, `/embed-{id}.html`. */
object VidNestResolver : JwPlayerHostResolver(
    displayName = "VidNest",
    hosts = HostMatcher(domains = setOf("vidnest.io", "vidnest.live")),
    playerUrl = { host, id, _ -> "https://$host/$id" },
)
