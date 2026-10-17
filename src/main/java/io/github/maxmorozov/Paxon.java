package io.github.maxmorozov;

import java.util.function.DoubleConsumer;

import static java.lang.StrictMath.scalb;

public class Paxon {
    /*
    Values are from
    Paxson V, "A Program for Testing IEEE Decimal-Binary Conversion"
    tables 1 and 2
     */
    private static final long[] PaxsonSignificands10 = {
            5L,
            69L,
            999L,
            7861L,
            75569L,
            928609L,
            9210917L,
            84863171L,
            653777767L,
            5232604057L,
            27235667517L,
            653532977297L,
            3142213164987L,
            46202199371337L,
            231010996856685L,
            9324754620109615L,
            78459735791271921L,
            272104041512242479L,
            6802601037806061975L,

            9L,
            85L,
            623L,
            3571L,
            81661L,
            920657L,
            4603285L,
            87575437L,
            245540327L,
            6138508175L,
            83356057653L,
            619534293513L,
            2335141086879L,
            36167929443327L,
            609610927149051L,
            3743626360493413L,
            94080055902682397L,
            899810892172646163L,
            7120190517612959703L,
    };

    private static final int[] PaxsonExponents10 = {
            125,
            267,
            -26,
            -34,
            -254,
            -261,
            80,
            114,
            273,
            -298,
            -109,
            -123,
            -294,
            -72,
            -73,
            212,
            49,
            200,
            198,

            -265,
            -37,
            100,
            263,
            153,
            -23,
            -24,
            -309,
            122,
            120,
            193,
            124,
            218,
            -159,
            -255,
            -165,
            -242,
            283,
            120,
    };

    /*
    Values are from
    Paxson V, "A Program for Testing IEEE Decimal-Binary Conversion"
    tables 3 and 4
     */
    private static final double[] PaxsonSignificands = {
            8_511_030_020_275_656L,
            5_201_988_407_066_741L,
            6_406_892_948_269_899L,
            8_431_154_198_732_492L,
            6_475_049_196_144_587L,
            8_274_307_542_972_842L,
            5_381_065_484_265_332L,
            6_761_728_585_499_734L,
            7_976_538_478_610_756L,
            5_982_403_858_958_067L,
            5_536_995_190_630_837L,
            7_225_450_889_282_194L,
            7_225_450_889_282_194L,
            8_703_372_741_147_379L,
            8_944_262_675_275_217L,
            7_459_803_696_087_692L,
            6_080_469_016_670_379L,
            8_385_515_147_034_757L,
            7_514_216_811_389_786L,
            8_397_297_803_260_511L,
            6_733_459_239_310_543L,
            8_091_450_587_292_794L,

            6_567_258_882_077_402L,
            6_712_731_423_444_934L,
            6_712_731_423_444_934L,
            5_298_405_411_573_037L,
            5_137_311_167_659_507L,
            6_722_280_709_661_868L,
            5_344_436_398_034_927L,
            8_369_123_604_277_281L,
            8_995_822_108_487_663L,
            8_942_832_835_564_782L,
            8_942_832_835_564_782L,
            8_942_832_835_564_782L,
            6_965_949_469_487_146L,
            6_965_949_469_487_146L,
            6_965_949_469_487_146L,
            7_487_252_720_986_826L,
            5_592_117_679_628_511L,
            8_887_055_249_355_788L,
            6_994_187_472_632_449L,
            8_797_576_579_012_143L,
            7_363_326_733_505_337L,
            8_549_497_411_294_502L,
    };

    private static final int[] PaxsonExponents = {
            -342,
            -824,
            237,
            72,
            99,
            726,
            -456,
            -57,
            376,
            377,
            93,
            710,
            709,
            117,
            -1,
            -707,
            -381,
            721,
            -828,
            -345,
            202,
            -473,

            952,
            535,
            534,
            -957,
            -144,
            363,
            -169,
            -853,
            -780,
            -383,
            -384,
            -385,
            -249,
            -250,
            -251,
            548,
            164,
            665,
            690,
            588,
            272,
            -448,
    };

    public static void testPaxsonDouble(DoubleConsumer consumer) {
        for (int i = 0; i < PaxsonSignificands.length; ++i) {
            consumer.accept(scalb(PaxsonSignificands[i], PaxsonExponents[i]));
        }
    }

    public static void testPaxsonDecimal(DecimalConsumer consumer) {
        for (int i = 0; i < PaxsonSignificands10.length; ++i) {
            consumer.accept(false, PaxsonSignificands10[i], PaxsonExponents10[i]);
        }
    }

    public interface DecimalConsumer {
        void accept(boolean negative, long significand, int exponent);
    }
}
