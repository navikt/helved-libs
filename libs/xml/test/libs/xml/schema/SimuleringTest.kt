package libs.xml.schema

import libs.xml.XMLMapper
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.ObjectFactory as RootFactory ;
import no.nav.system.os.entiteter.oppdragskjema.ObjectFactory as OppdragFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory;
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.assertj.core.api.Assertions.assertThat

class SimuleringTest {
    private val mapper = XMLMapper<SimulerBeregningRequest>(false)

    @Test
    fun `can serialize`() {
        val expected = xmlString
        val actual = mapper.writeValueAsString(simulering())
        assertEquals(expected, actual)
    }

    @Test
    fun `can deserialize`() {
        val expected = simulering()
        val actual = mapper.readValue(xmlString)
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }
}

private val rootFactory = RootFactory()
private val objectFactory = ObjectFactory()
private val oppdragFactory = OppdragFactory()

private fun simulering(
    oppdragslinjer: List<Oppdragslinje> = listOf(oppdragslinje()),
    kodeEndring: String = "NY", // NY/ENDR
    fagområde: String = "ABC",
    fagsystemId: String = "1", // sakid
    oppdragGjelderId: String = "12345678910", // personident
    saksbehId: String = "Z999999",
    enhet: String? = null, // betalende enhet (lokalkontor)
): SimulerBeregningRequest {
    val oppdrag = objectFactory.createOppdrag().apply {
        this.kodeEndring = kodeEndring
        this.kodeFagomraade = fagområde
        this.fagsystemId = fagsystemId
        this.utbetFrekvens = "MND"
        this.oppdragGjelderId = oppdragGjelderId
        this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
        this.saksbehId = saksbehId
        this.enhets.addAll(enheter(enhet))
        oppdragslinjes.addAll(oppdragslinjer)
    }
    return rootFactory.createSimulerBeregningRequest().apply {
        request = objectFactory.createSimulerBeregningRequest().apply {
            this.oppdrag = oppdrag
        }
    }
}

private fun oppdragslinje(
    delytelsesId: String = "1A",
    sats: Long = 700,
    datoVedtakFom: LocalDate = LocalDate.of(2025, 11, 3),
    datoVedtakTom: LocalDate = LocalDate.of(2025, 11, 7),
    typeSats: String = "DAG", // DAG/DAG7/MND/ENG
    refDelytelsesId: String? = null,
    kodeEndring: String = "NY", // NY/ENDR
    opphør: LocalDate? = null,
    fagsystemId: String = "1", // sakid
    klassekode: String = "SWAG",
    saksbehId: String = "Z999999", // saksbehandler
    beslutterId: String = "Z999999", // beslutter
    utbetalesTilId: String = "12345678910", // personident
): Oppdragslinje {
    val attestant = oppdragFactory.createAttestant().apply {
        this.attestantId = beslutterId
    }
    return objectFactory.createOppdragslinje().apply {
        this.kodeEndringLinje = kodeEndring
        opphør?.let {
            this.kodeStatusLinje = KodeStatusLinje.OPPH
            this.datoStatusFom = opphør.format()
        }
        refDelytelsesId?.let {
            this.refDelytelseId = refDelytelsesId
            this.refFagsystemId = fagsystemId
        }
        this.delytelseId = delytelsesId
        this.kodeKlassifik = klassekode
        this.datoVedtakFom = datoVedtakFom.format()
        this.datoVedtakTom = datoVedtakTom.format()
        this.sats = BigDecimal.valueOf(sats)
        this.fradragTillegg = FradragTillegg.T
        this.typeSats = typeSats
        this.brukKjoreplan = "N"
        this.saksbehId = saksbehId
        this.utbetalesTilId = utbetalesTilId
        this.attestants.add(attestant)
    }
}

private fun enheter(enhet: String? = null): List<Enhet> {
    val bos = oppdragFactory.createEnhet().apply {
        this.enhet = enhet ?: "8020"
        this.typeEnhet = "BOS"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    val beh = oppdragFactory.createEnhet().apply {
        this.enhet = "8020"
        this.typeEnhet = "BEH"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    return when (enhet) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}

private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("YYYY-MM-dd"))

private val xmlString: String = """<ns3:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/entiteter/oppdragSkjema" xmlns:ns3="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
    <request>
        <oppdrag>
            <kodeEndring>NY</kodeEndring>
            <kodeFagomraade>ABC</kodeFagomraade>
            <fagsystemId>1</fagsystemId>
            <utbetFrekvens>MND</utbetFrekvens>
            <oppdragGjelderId>12345678910</oppdragGjelderId>
            <datoOppdragGjelderFom>1999-01-01</datoOppdragGjelderFom>
            <saksbehId>Z999999</saksbehId>
            <ns2:enhet>
                <typeEnhet>BOS</typeEnhet>
                <enhet>8020</enhet>
                <datoEnhetFom>1970-01-01</datoEnhetFom>
            </ns2:enhet>
            <oppdragslinje>
                <kodeEndringLinje>NY</kodeEndringLinje>
                <delytelseId>1A</delytelseId>
                <kodeKlassifik>SWAG</kodeKlassifik>
                <datoVedtakFom>2025-11-03</datoVedtakFom>
                <datoVedtakTom>2025-11-07</datoVedtakTom>
                <sats>700</sats>
                <fradragTillegg>T</fradragTillegg>
                <typeSats>DAG</typeSats>
                <brukKjoreplan>N</brukKjoreplan>
                <saksbehId>Z999999</saksbehId>
                <utbetalesTilId>12345678910</utbetalesTilId>
                <ns2:attestant>
                    <attestantId>Z999999</attestantId>
                </ns2:attestant>
            </oppdragslinje>
        </oppdrag>
    </request>
</ns3:simulerBeregningRequest>""".trimIndent()

