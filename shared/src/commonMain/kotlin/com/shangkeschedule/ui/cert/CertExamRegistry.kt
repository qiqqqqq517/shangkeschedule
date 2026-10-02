package com.shangkeschedule.ui.cert

import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.cert_region_beijing
import shangkeschedule.shared.generated.resources.cert_region_tianjin
import shangkeschedule.shared.generated.resources.cert_region_hebei
import shangkeschedule.shared.generated.resources.cert_region_shanxi
import shangkeschedule.shared.generated.resources.cert_region_neimenggu
import shangkeschedule.shared.generated.resources.cert_region_liaoning
import shangkeschedule.shared.generated.resources.cert_region_jilin
import shangkeschedule.shared.generated.resources.cert_region_heilongjiang
import shangkeschedule.shared.generated.resources.cert_region_shanghai
import shangkeschedule.shared.generated.resources.cert_region_jiangsu
import shangkeschedule.shared.generated.resources.cert_region_zhejiang
import shangkeschedule.shared.generated.resources.cert_region_anhui
import shangkeschedule.shared.generated.resources.cert_region_fujian
import shangkeschedule.shared.generated.resources.cert_region_jiangxi
import shangkeschedule.shared.generated.resources.cert_region_shandong
import shangkeschedule.shared.generated.resources.cert_region_henan
import shangkeschedule.shared.generated.resources.cert_region_hubei
import shangkeschedule.shared.generated.resources.cert_region_hunan
import shangkeschedule.shared.generated.resources.cert_region_guangdong
import shangkeschedule.shared.generated.resources.cert_region_guangxi
import shangkeschedule.shared.generated.resources.cert_region_hainan
import shangkeschedule.shared.generated.resources.cert_region_chongqing
import shangkeschedule.shared.generated.resources.cert_region_sichuan
import shangkeschedule.shared.generated.resources.cert_region_guizhou
import shangkeschedule.shared.generated.resources.cert_region_yunnan
import shangkeschedule.shared.generated.resources.cert_region_xizang
import shangkeschedule.shared.generated.resources.cert_region_shaanxi
import shangkeschedule.shared.generated.resources.cert_region_gansu
import shangkeschedule.shared.generated.resources.cert_region_qinghai
import shangkeschedule.shared.generated.resources.cert_region_ningxia
import shangkeschedule.shared.generated.resources.cert_region_xinjiang
import shangkeschedule.shared.generated.resources.cert_module_cet
import shangkeschedule.shared.generated.resources.cert_module_cet_desc
import shangkeschedule.shared.generated.resources.cert_module_ncre
import shangkeschedule.shared.generated.resources.cert_module_ncre_desc
import shangkeschedule.shared.generated.resources.cert_module_ntce
import shangkeschedule.shared.generated.resources.cert_module_ntce_desc
import shangkeschedule.shared.generated.resources.cert_module_psc
import shangkeschedule.shared.generated.resources.cert_module_psc_desc
import shangkeschedule.shared.generated.resources.cert_module_zsb
import shangkeschedule.shared.generated.resources.cert_module_zsb_desc

/**
 * 一个考证查分模块（v4.66.0）。
 *
 * @param id 稳定标识，同时作为凭据的存储键后缀（改 id 等于丢掉用户已存的凭据）
 * @param titleRes 模块名
 * @param descRes 副标题（说明该考试查分需要什么凭据）
 * @param entryUrl 官方查分入口（**域级地址**，不写容易失效的深链）；[regions] 非空时本字段不使用
 * @param regions 需要「按省份分流」的考试的省级官方入口（专升本）；为空 = 全国统一入口
 */
data class CertExamModule(
    val id: String,
    val titleRes: StringResource,
    val descRes: StringResource,
    val entryUrl: String = "",
    val regions: List<CertExamRegion> = emptyList()
)

/**
 * 省级官方入口（v4.66.0，B5 专升本）。
 *
 * @param nameRes 省级行政区名（三语）
 * @param url 该省（市 / 自治区）教育考试院官网根地址
 */
data class CertExamRegion(
    val nameRes: StringResource,
    val url: String
)

/**
 * 考证查分注册表（v4.66.0）。
 *
 * 产品边界：**只做入口与凭据备忘，不做代查**。
 * 各考试的查分通道几乎每年都在变（四六级从学信网换到中国教育考试网、普通话各省站点不一），
 * 应用无法保证「代查」所需的参数与接口长期有效；而一旦代查，用户的姓名 / 准考证号
 * 就必须发往第三方，与本项目「不收集用户数据」的定位冲突（见应用内隐私政策）。
 *
 * 因此这里只登记两件稳定的事：**官网入口**（域级地址，不写容易失效的深链）与
 * **需要什么凭据**（用于界面副标题与凭据表单）。
 *
 * 新增考试：在此加一条 + 在 `values/strings.xml` 加标题/副标题两条文案即可。
 * 入口下线时只需改这一处的 url。
 */
object CertExamRegistry {

    /** 中国教育考试网成绩查询平台（CET / NCRE 等均由此进入）。 */
    private const val NEEA_SCORE_QUERY = "https://cjcx.neea.edu.cn/"

    /** 中小学教师资格考试（NTCE）成绩查询。 */
    private const val NTCE_SCORE_QUERY = "https://ntce.neea.edu.cn/ntce/"

    /** 国家普通话水平测试在线报名系统（含成绩查询）。 */
    private const val PSC_ONLINE = "https://bm.cltt.org/"

    /**
     * 专升本（统招）省级官方入口：31 个省级行政区。
     *
     * 专升本**由各省自主组织**（科目、总分、分数线、成绩发布时间都不一样），全国没有统一查分站，
     * 所以这里登记各省教育考试院（招生考试机构）的**官网根地址**，由用户在界面上选省份。
     * 全部域名于 2026-10-01 逐个 HTTP 校验：优先 https（校验通过才用），仅 https 不通时退回 http；
     * 其中河南 / 湖北 / 重庆的站点会对非浏览器请求返回 403 / 412 / 503，属站点侧反爬，域名本身无误。
     */
    private val ZSB_REGIONS: List<CertExamRegion> = listOf(
        CertExamRegion(Res.string.cert_region_beijing, "https://www.bjeea.cn/"),
        CertExamRegion(Res.string.cert_region_tianjin, "http://www.zhaokao.net/"),
        CertExamRegion(Res.string.cert_region_hebei, "https://www.hebeea.edu.cn/"),
        CertExamRegion(Res.string.cert_region_shanxi, "http://www.sxkszx.cn/"),
        CertExamRegion(Res.string.cert_region_neimenggu, "https://www.nm.zsks.cn/"),
        CertExamRegion(Res.string.cert_region_liaoning, "https://www.lnzsks.com/"),
        CertExamRegion(Res.string.cert_region_jilin, "https://www.jleea.com.cn/"),
        CertExamRegion(Res.string.cert_region_heilongjiang, "https://www.lzk.hl.cn/"),
        CertExamRegion(Res.string.cert_region_shanghai, "https://www.shmeea.edu.cn/"),
        CertExamRegion(Res.string.cert_region_jiangsu, "https://www.jseea.cn/"),
        CertExamRegion(Res.string.cert_region_zhejiang, "https://www.zjzs.net/"),
        CertExamRegion(Res.string.cert_region_anhui, "https://www.ahzsks.cn/"),
        CertExamRegion(Res.string.cert_region_fujian, "https://www.eeafj.cn/"),
        CertExamRegion(Res.string.cert_region_jiangxi, "https://www.jxeea.cn/"),
        CertExamRegion(Res.string.cert_region_shandong, "https://www.sdzk.cn/"),
        CertExamRegion(Res.string.cert_region_henan, "https://www.haeea.cn/"),
        CertExamRegion(Res.string.cert_region_hubei, "http://www.hbea.edu.cn/"),
        CertExamRegion(Res.string.cert_region_hunan, "https://www.hneeb.cn/"),
        CertExamRegion(Res.string.cert_region_guangdong, "https://eea.gd.gov.cn/"),
        CertExamRegion(Res.string.cert_region_guangxi, "https://www.gxeea.cn/"),
        CertExamRegion(Res.string.cert_region_hainan, "https://ea.hainan.gov.cn/"),
        CertExamRegion(Res.string.cert_region_chongqing, "https://www.cqksy.cn/"),
        CertExamRegion(Res.string.cert_region_sichuan, "https://www.sceea.cn/"),
        CertExamRegion(Res.string.cert_region_guizhou, "http://www.gzszk.com/"),
        CertExamRegion(Res.string.cert_region_yunnan, "https://www.ynzs.cn/"),
        CertExamRegion(Res.string.cert_region_xizang, "http://zsks.edu.xizang.gov.cn/"),
        CertExamRegion(Res.string.cert_region_shaanxi, "https://www.sneea.cn/"),
        CertExamRegion(Res.string.cert_region_gansu, "https://www.ganseea.cn/"),
        CertExamRegion(Res.string.cert_region_qinghai, "https://www.qhjyks.com/"),
        CertExamRegion(Res.string.cert_region_ningxia, "https://www.nxjyks.cn/"),
        CertExamRegion(Res.string.cert_region_xinjiang, "https://www.xjzk.gov.cn/"),
    )

    val modules: List<CertExamModule> = listOf(
        CertExamModule(
            id = "cet",
            titleRes = Res.string.cert_module_cet,
            descRes = Res.string.cert_module_cet_desc,
            entryUrl = NEEA_SCORE_QUERY
        ),
        CertExamModule(
            id = "ncre",
            titleRes = Res.string.cert_module_ncre,
            descRes = Res.string.cert_module_ncre_desc,
            entryUrl = NEEA_SCORE_QUERY
        ),
        CertExamModule(
            id = "ntce",
            titleRes = Res.string.cert_module_ntce,
            descRes = Res.string.cert_module_ntce_desc,
            entryUrl = NTCE_SCORE_QUERY
        ),
        CertExamModule(
            id = "psc",
            titleRes = Res.string.cert_module_psc,
            descRes = Res.string.cert_module_psc_desc,
            entryUrl = PSC_ONLINE
        ),
        CertExamModule(
            id = "zsb",
            titleRes = Res.string.cert_module_zsb,
            descRes = Res.string.cert_module_zsb_desc,
            regions = ZSB_REGIONS
        )
    )
}
