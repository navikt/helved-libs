package libs.xml.schema

import libs.xml.XMLMapper
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AvstemmingTest {
    private val mapper = XMLMapper<Avstemmingsdata>()

    @Test
    fun `read value`() {
        val actual = mapper.readValue(xml)
        assertThat(actual).usingRecursiveComparison().isEqualTo(avstemmingsdata)
    }

    @Test
    fun `write value as string`() {
        val actual = mapper.writeValueAsString(avstemmingsdata)
        assertEquals(xml, actual)
    }
}

private val xml = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:avstemmingsdata xmlns:ns2="http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1">
    <aksjon>
        <aksjonType>DATA</aksjonType>
        <kildeType>AVLEV</kildeType>
        <avstemmingType>GRSN</avstemmingType>
        <avleverendeKomponentKode>DP</avleverendeKomponentKode>
        <mottakendeKomponentKode>OS</mottakendeKomponentKode>
        <underkomponentKode>DP</underkomponentKode>
        <nokkelFom>2024-05-01-12.58.47.126121</nokkelFom>
        <nokkelTom>2024-06-23-12.58.47.126165</nokkelTom>
        <avleverendeAvstemmingId>uzQl-mb1TyuWypVyWYo67w</avleverendeAvstemmingId>
        <brukerId>DP</brukerId>
    </aksjon>
    <total>
        <totalAntall>1</totalAntall>
        <totalBelop>100</totalBelop>
        <fortegn>T</fortegn>
    </total>
    <periode>
        <datoAvstemtFom>2024052312</datoAvstemtFom>
        <datoAvstemtTom>2024052312</datoAvstemtTom>
    </periode>
    <grunnlag>
        <godkjentAntall>0</godkjentAntall>
        <godkjentBelop>0</godkjentBelop>
        <godkjentFortegn>T</godkjentFortegn>
        <varselAntall>0</varselAntall>
        <varselBelop>0</varselBelop>
        <varselFortegn>T</varselFortegn>
        <avvistAntall>0</avvistAntall>
        <avvistBelop>0</avvistBelop>
        <avvistFortegn>T</avvistFortegn>
        <manglerAntall>1</manglerAntall>
        <manglerBelop>100</manglerBelop>
        <manglerFortegn>T</manglerFortegn>
    </grunnlag>
    <detalj>
        <detaljType>MANG</detaljType>
        <offnr>12345678911</offnr>
        <avleverendeTransaksjonNokkel>35AAroJHGBAg52wELAnf</avleverendeTransaksjonNokkel>
        <tidspunkt>2024-05-23-12.58.46.841066</tidspunkt>
    </detalj>
</ns2:avstemmingsdata>

""".trimIndent()

private val avstemmingsdata
    get() = Avstemmingsdata().apply {
        aksjon = aksjonsdata
        total = totaldata
        periode = periodedata
        grunnlag = grunnlagsdata
        detaljs.addAll(listOf(detaljdata))
    }

val aksjonsdata
    get() = Aksjonsdata().apply {
        aksjonType = AksjonType.DATA
        kildeType = KildeType.AVLEV
        avstemmingType = AvstemmingType.GRSN
        avleverendeKomponentKode = "DP"
        mottakendeKomponentKode = "OS"
        underkomponentKode = "DP"
        nokkelFom = "2024-05-01-12.58.47.126121"
        nokkelTom = "2024-06-23-12.58.47.126165"
        avleverendeAvstemmingId = "uzQl-mb1TyuWypVyWYo67w"
        brukerId = "DP"
    }

val detaljdata
    get() = Detaljdata().apply {
        detaljType = DetaljType.MANG
        offnr = "12345678911"
        avleverendeTransaksjonNokkel = "35AAroJHGBAg52wELAnf"
        tidspunkt = "2024-05-23-12.58.46.841066"
    }

val grunnlagsdata
    get() = Grunnlagsdata().apply {
        godkjentAntall = 0
        godkjentBelop = BigDecimal.ZERO
        godkjentFortegn = Fortegn.T
        varselAntall = 0
        varselBelop = BigDecimal.ZERO
        varselFortegn = Fortegn.T
        avvistAntall = 0
        avvistBelop = BigDecimal.ZERO
        avvistFortegn = Fortegn.T
        manglerAntall = 1
        manglerBelop = BigDecimal(100)
        manglerFortegn = Fortegn.T
    }

val periodedata
    get() = Periodedata().apply {
        datoAvstemtFom = "2024052312"
        datoAvstemtTom = "2024052312"
    }

val totaldata
    get() = Totaldata().apply {
        totalAntall = 1
        totalBelop = BigDecimal(100)
        fortegn = Fortegn.T
    }