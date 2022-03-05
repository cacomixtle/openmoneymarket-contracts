package finance.omm.score.core.reward.unit.test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import finance.omm.libs.address.Contracts;
import finance.omm.libs.structs.AddressDetail;
import finance.omm.libs.structs.TypeWeightStruct;
import finance.omm.libs.structs.WeightStruct;
import finance.omm.score.core.reward.RewardWeightControllerImpl;
import finance.omm.utils.constants.TimeConstants;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

public class RewardControllerUnitTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private Account owner;
    private Score score;
    private RewardWeightControllerImpl scoreSpy;

    private static final String TYPE_ID_PREFIX = "Key-";

    Address[] addresses = new Address[]{
            Account.newScoreAccount(1001).getAddress(),
            Account.newScoreAccount(1002).getAddress(),
            Account.newScoreAccount(1003).getAddress(),
            Account.newScoreAccount(1004).getAddress(),
            Account.newScoreAccount(1005).getAddress(),
            Account.newScoreAccount(1006).getAddress(),
            Account.newScoreAccount(1007).getAddress(),
            Account.newScoreAccount(1008).getAddress(),
            Account.newScoreAccount(1009).getAddress(),
            Account.newScoreAccount(1010).getAddress(),
            Account.newScoreAccount(1011).getAddress(),
    };


    private Map<Contracts, Account> mockAddress = new HashMap<>() {{
        put(Contracts.ADDRESS_PROVIDER, Account.newScoreAccount(101));
        put(Contracts.REWARDS, Account.newScoreAccount(102));
    }};
    private final BigInteger startTimestamp = getTimestamp();


    @BeforeEach
    void setup() throws Exception {
        owner = sm.createAccount(100);
        score = sm.deploy(owner, RewardWeightControllerImpl.class,
                mockAddress.get(Contracts.ADDRESS_PROVIDER).getAddress().toString(),
                startTimestamp);
        AddressDetail[] addressDetails = mockAddress.entrySet().stream().map(e -> {
            AddressDetail ad = new AddressDetail();
            ad.address = e.getValue().getAddress();
            ad.name = e.getKey().toString();
            return ad;
        }).toArray(AddressDetail[]::new);

        Object[] params = new Object[]{
                addressDetails
        };
        score.invoke(mockAddress.get(Contracts.ADDRESS_PROVIDER), "setAddresses", params);

        scoreSpy = (RewardWeightControllerImpl) spy(score.getInstance());
        score.setInstance(scoreSpy);

    }

    private void addType(Account account, String key) {
        score.invoke(account, "addType", key, Boolean.FALSE, Account.newScoreAccount(0).getAddress());
    }

    @DisplayName("verify token inflation rate")
    @Test
    public void testTokenDistributionPerDay() {
        BigInteger tokenIn5Years = (BigInteger) this.score.call("tokenDistributionPerDay", BigInteger.valueOf(5 * 365));
        BigInteger tokenIn6Years = (BigInteger) this.score.call("tokenDistributionPerDay", BigInteger.valueOf(6 * 365));
        BigDecimal inflation_56 = new BigDecimal(tokenIn6Years.subtract(tokenIn5Years)).divide(
                new BigDecimal(tokenIn5Years), 2, RoundingMode.HALF_UP);
        assertEquals(0.03, inflation_56.doubleValue());
        BigInteger tokenIn7Years = (BigInteger) this.score.call("tokenDistributionPerDay", BigInteger.valueOf(7 * 365));
        BigDecimal inflation_67 = new BigDecimal(tokenIn7Years.subtract(tokenIn6Years)).divide(
                new BigDecimal(tokenIn6Years), 2, RoundingMode.HALF_UP);
        assertEquals(0.03, inflation_67.doubleValue());
    }

    @DisplayName("add type")
    @Test
    public void testAddType() {
        addType(mockAddress.get(Contracts.REWARDS), "Key-1");

        Executable call = () -> addType(mockAddress.get(Contracts.REWARDS), "Key-1");
        expectErrorMessage(call, "duplicate key (Key-1)");
    }

    @DisplayName("invalid total type weight")
    @Test
    public void testSetInvalidTypeWeight() {
        addType(mockAddress.get(Contracts.REWARDS), "Key-1");
        TypeWeightStruct[] weights = new TypeWeightStruct[1];

        TypeWeightStruct struct = new TypeWeightStruct();
        struct.weight = BigInteger.TEN.multiply(ICX).divide(BigInteger.valueOf(100));
        struct.key = "Key-1";
        weights[0] = struct;

        Object[] params = new Object[]{weights, BigInteger.ZERO};
        Executable call = () -> score.invoke(owner, "setTypeWeight", params);
        expectErrorMessage(call, "Total percentage is not equals to 100%");
    }

    @DisplayName("add type weight")
    @Test
    public void testSetTypeWeight() {
        initTypeWeight(BigInteger.ZERO, 10L, 20L, 70L);

        Map<Integer, BigInteger> snapshots = new HashMap<>();
        snapshots.put(1, getTimestamp());

        verify(scoreSpy).SetTypeWeight(getTimestamp(), "Type weight updated");

        BigInteger checkpoints = (BigInteger) score.call("getTypeCheckpointCount");
        assertEquals(BigInteger.ONE, checkpoints);

        setTypeWeight(snapshots.get(1), new HashMap<>() {{
            put(1, 40L);
            put(3, 40L);
        }});

        checkpoints = (BigInteger) score.call("getTypeCheckpointCount");
        assertEquals(BigInteger.ONE, checkpoints);

        Executable call = () -> setTypeWeight(snapshots.get(1), new HashMap<>() {{
            put(1, 40L);
            put(2, 0L);
        }});

        expectErrorMessage(call, "Total percentage is not equals to 100%");


    }

    @DisplayName("type weight snapshot")
    @Test
    public void testTypeWeightSnapshot() {
        initTypeWeight(BigInteger.ZERO, 10L, 20L, 30L, 40L);

        Map<Integer, BigInteger> snapshots = new HashMap<>();
        Map<Integer, Map<Integer, Long>> values = new HashMap<>();
        snapshots.put(1, getTimestamp());
        values.put(1, new HashMap<>() {{
            put(1, 10L);
            put(2, 20L);
            put(3, 30L);
            put(4, 40L);
        }});
        Random r = new Random();
        for (int i = 2; i <= 10; i++) {
            sm.getBlock().increase(r.nextInt(1000) + 1);
            long a = r.nextInt(25) + 1;
            long b = r.nextInt(25) + 1;
            long c = r.nextInt(25) + 1;
            long d = 100 - a - b - c;
            Map<Integer, Long> map = new HashMap<>() {{
                put(1, a);
                put(2, b);
                put(3, c);
                put(4, d);
            }};
            setTypeWeight(BigInteger.ZERO, map);
            snapshots.put(i, getTimestamp());
            values.put(i, map);
        }

        for (int i = 10; i > 1; i--) {
            BigInteger timestamp = snapshots.get(i);
            Map<String, BigInteger> nextTime = (Map<String, BigInteger>) score.call("getTypeWeightByTimestamp",
                    timestamp.add(BigInteger.ONE));
            Map<String, BigInteger> exactTime = (Map<String, BigInteger>) score.call("getTypeWeightByTimestamp",
                    timestamp);
            Map<String, BigInteger> prevTime = (Map<String, BigInteger>) score.call("getTypeWeightByTimestamp",
                    timestamp.subtract(BigInteger.ONE));

            Map<Integer, Long> value = values.get(i);
            for (Map.Entry<Integer, Long> entry : value.entrySet()) {
                String id = "Key-" + entry.getKey();
                BigInteger next = nextTime.get(id);
                BigInteger exact = exactTime.get(id);
                BigInteger expected = BigInteger.valueOf(entry.getValue())
                        .multiply(ICX)
                        .divide(BigInteger.valueOf(100));
                assertEquals(expected, next, "next data not match at " + id);
                assertEquals(expected, exact, "exact data not match at " + id);
            }

            value = values.get(i - 1);
            for (Map.Entry<Integer, Long> entry : value.entrySet()) {
                String id = "Key-" + entry.getKey();
                BigInteger prev = prevTime.get(id);
                BigInteger expected = BigInteger.valueOf(entry.getValue())
                        .multiply(ICX)
                        .divide(BigInteger.valueOf(100));

                assertEquals(expected, prev, "previous data not match at " + id);
            }
        }

    }

    @DisplayName("test add asset")
    @Test
    public void testAddAsset() {
        initTypeWeight(BigInteger.ZERO, 10L, 20L, 30L, 40L);
        String type = "Key-" + 1;
        addAsset(type, addresses[1]);
    }

    @DisplayName("test asset weight")
    @Test
    public void testSetAssetWeight() {
        initTypeWeight(BigInteger.ZERO, 25L, 75L);
        Map<String, BigInteger> snapshots = new HashMap<>();

        String typeId = TYPE_ID_PREFIX + 1;
//        addAsset(1, typeId);

        String typeId_2 = TYPE_ID_PREFIX + 2;
//        addAsset(2, typeId_2);
        Map<Address, Long> values = new HashMap<>() {{
            put(addresses[0], 10L);
            put(addresses[1], 20L);
            put(addresses[2], 30L);
            put(addresses[3], 40L);
        }};
        initAssetWeight(BigInteger.ZERO, 1, values);
        snapshots.put(typeId, getTimestamp());

        BigInteger checkpoints = (BigInteger) score.call("getAssetCheckpointCount",
                typeId);
        assertEquals(BigInteger.ONE, checkpoints);

        BigInteger futureTime = getTimestamp().add(TimeConstants.SECOND.multiply(BigInteger.valueOf(100)));
        values = new HashMap<>() {{
            put(addresses[4], 50L);
            put(addresses[5], 50L);
        }};
        initAssetWeight(futureTime, 2, values);
        snapshots.put(typeId_2, futureTime);

        checkpoints = (BigInteger) score.call("getAssetCheckpointCount",
                typeId_2);
        assertEquals(BigInteger.ONE, checkpoints);

        setAssetWeight(BigInteger.ZERO, typeId, new HashMap<>() {{
            put(addresses[0], 40L);
            put(addresses[3], 10L);
        }});
        checkpoints = (BigInteger) score.call("getAssetCheckpointCount",
                typeId);
        assertEquals(BigInteger.TWO, checkpoints);

        //shouldn't able to set new asset weight if future weight is already exists
        BigInteger current = getTimestamp().add(TimeConstants.SECOND.multiply(BigInteger.TWO));
        Executable call = () -> setAssetWeight(BigInteger.ZERO, typeId_2, new HashMap<>() {{
            put(addresses[1], 40L);
            put(addresses[2], 60L);
        }});
        expectErrorMessage(call,
                "latest " + snapshots.get(typeId_2) + " checkpoint exists than " + current);


    }


    @DisplayName("asset weight snapshot")
    @Test
    public void testAssetWeightSnapshot() {
        initTypeWeight(BigInteger.ZERO, 25L, 75L);

        String typeId = TYPE_ID_PREFIX + 1;

        String typeId_2 = TYPE_ID_PREFIX + 2;
        Map<Address, Long> addressMap = new HashMap<>() {{
            put(addresses[0], 10L);
            put(addresses[1], 20L);
            put(addresses[2], 30L);
            put(addresses[3], 40L);
        }};
        initAssetWeight(BigInteger.ZERO, 1, addressMap);
        Map<Integer, BigInteger> snapshots = new HashMap<>();
        Map<Integer, Map<Address, Long>> values = new HashMap<>();
        snapshots.put(1, getTimestamp());
        values.put(1, addressMap);

        Map<Address, Long> addressMap2 = new HashMap<>() {{
            put(addresses[4], 25L);
            put(addresses[5], 25L);
            put(addresses[6], 25L);
            put(addresses[7], 25L);
        }};

        initAssetWeight(BigInteger.ZERO, 2, addressMap2);
        snapshots.put(2, getTimestamp());
        values.put(2, addressMap2);
        Random r = new Random();

        for (int i = 3; i <= 20; i++) {
            sm.getBlock().increase(r.nextInt(1000) + 1);
            long a = r.nextInt(25) + 1;
            long b = r.nextInt(25) + 1;
            long c = r.nextInt(25) + 1;
            long d = 100 - a - b - c;

            String type_id = i % 2 == 0 ? typeId_2 : typeId;
            int startIndex = i % 2 == 0 ? 4 : 0;
            Map<Address, Long> map = new HashMap<>() {{
                put(addresses[startIndex + 0], a);
                put(addresses[startIndex + 1], b);
                put(addresses[startIndex + 2], c);
                put(addresses[startIndex + 3], d);
            }};
            setAssetWeight(BigInteger.ZERO, type_id, map);
            snapshots.put(i, getTimestamp());
            values.put(i, map);
        }

        for (int i = 20; i > 2; i--) {
            BigInteger timestamp = snapshots.get(i);
            String type_id = i % 2 == 0 ? typeId_2 : typeId;
            Map<String, BigInteger> nextTime = (Map<String, BigInteger>) score.call("getAssetWeightByTimestamp",
                    type_id,
                    timestamp.add(BigInteger.ONE));
            Map<String, BigInteger> exactTime = (Map<String, BigInteger>) score.call("getAssetWeightByTimestamp",
                    type_id,
                    timestamp);
            Map<String, BigInteger> prevTime = (Map<String, BigInteger>) score.call("getAssetWeightByTimestamp",
                    type_id,
                    timestamp.subtract(BigInteger.ONE));

            Map<Address, Long> value = values.get(i);
            for (Map.Entry<Address, Long> entry : value.entrySet()) {
                String id = entry.getKey().toString();
                BigInteger next = nextTime.get(id);
                BigInteger exact = exactTime.get(id);
                BigInteger expected = BigInteger.valueOf(entry.getValue())
                        .multiply(ICX)
                        .divide(BigInteger.valueOf(100));
                assertEquals(expected, next, "next data not match at " + id);
                assertEquals(expected, exact, "exact data not match at " + id);
            }

            value = values.get(i - 2);
            for (Map.Entry<Address, Long> entry : value.entrySet()) {
                String id = entry.getKey().toString();
                BigInteger prev = prevTime.get(id);
                BigInteger expected = BigInteger.valueOf(entry.getValue())
                        .multiply(ICX)
                        .divide(BigInteger.valueOf(100));

                assertEquals(expected, prev, "previous data not match at " + id);
            }
        }

    }

    @DisplayName("Integrate index test")
    @Test
    public void testIntegrateIndex() {
        sm.getBlock().increase(30 * 86400 / 2 - 3008);
        initTypeWeight(BigInteger.ZERO, 25L, 75L); //3 calls

        String typeId = TYPE_ID_PREFIX + 1;
        Map<Address, Long> values = new HashMap<>() {{
            put(addresses[0], 10L);
            put(addresses[1], 20L);
            put(addresses[2], 30L);
            put(addresses[3], 40L);
        }};
        initAssetWeight(BigInteger.ZERO, 1, values); //5 calls
        BigInteger currentTimestamp = getTimestamp();
        sm.getBlock().increase(1000);
        Object[] params = new Object[]{
                addresses[0],
                BigInteger.valueOf(100).multiply(ICX),
                currentTimestamp
        };

        BigInteger index = (BigInteger) score.call("getIntegrateIndex", params);

        BigInteger time_delta = getTimestamp().subtract(currentTimestamp).divide(TimeConstants.SECOND);

        /*
          type percentage 25%
          asset percentage 10%
          daily token distribution 1_000_000
         */
        float emissionPerSecond = 0.10f * 0.25f * 1_000_000 / TimeConstants.DAY_IN_MICRO_SECONDS.floatValue();
        float expectedIndex = emissionPerSecond * time_delta.floatValue() / 100;

        assertEquals(expectedIndex, index.floatValue() / ICX.floatValue());

        sm.getBlock().increase(599);
        setTypeWeight(BigInteger.ZERO, new HashMap<>() {{
            put(1, 40L);
            put(2, 60L);
        }}); //1 calls
        /*
          no change in rate till now
          type percentage 25%
          asset percentage 10%
          daily token distribution 1_000_000
         */
        time_delta = getTimestamp().subtract(currentTimestamp).divide(TimeConstants.SECOND);
        expectedIndex = emissionPerSecond * time_delta.floatValue() / 100;
        index = (BigInteger) score.call("getIntegrateIndex", params);
        currentTimestamp = getTimestamp();
        assertEquals(expectedIndex, index.floatValue() / ICX.floatValue(), 0.00001);

        sm.getBlock().increase(599);
        setAssetWeight(BigInteger.ZERO, typeId, new HashMap<>() {{
            put(addresses[0], 50L);
            put(addresses[2], 10L);
            put(addresses[3], 20L);
        }}); //1 calls
        /*
          no change in rate till now
          type percentage 40%
          asset percentage 10%
          daily token distribution 1_000_000
         */

        index = (BigInteger) score.call("getIntegrateIndex", params);
        time_delta = getTimestamp().subtract(currentTimestamp).divide(TimeConstants.SECOND);
        emissionPerSecond = 0.10f * 0.40f * 1_000_000 / TimeConstants.DAY_IN_MICRO_SECONDS.floatValue();
        expectedIndex = expectedIndex + emissionPerSecond * time_delta.floatValue()
                / 100; //integrateIndex -> {BigInteger@3661} "555092592592592592637" //integrateIndex -> {BigInteger@3694} "925636574074074074548"
        currentTimestamp = getTimestamp();

        assertEquals(expectedIndex, index.floatValue() / ICX.floatValue(), 0.00001);


        /*
        200 block ahead of month from start timestamp
         */
        sm.getBlock().increase(1000);
        /*
          type percentage 40%
          asset percentage 50%
          daily token distribution 1_000_000
         */

        time_delta = getTimestamp()
                .subtract(currentTimestamp).divide(TimeConstants.SECOND).subtract(BigInteger.valueOf(402));
        System.out.println("time_delta = "
                + time_delta);
        emissionPerSecond = 0.40f * 0.50f * 1_000_000 / TimeConstants.DAY_IN_MICRO_SECONDS.floatValue();
        expectedIndex = expectedIndex + emissionPerSecond * time_delta.floatValue() / 100;

        index = (BigInteger) score.call("getIntegrateIndex", params);

        /*
          type percentage 40%
          asset percentage 50%
          daily token distribution 400_000 after 1 month
         */
        time_delta = BigInteger.valueOf(402);
        emissionPerSecond = 0.40f * 0.50f * 400_000 / TimeConstants.DAY_IN_MICRO_SECONDS.floatValue();
        expectedIndex = expectedIndex + emissionPerSecond * time_delta.floatValue() / 100;
        assertEquals(expectedIndex, index.floatValue() / ICX.floatValue(), 0.00001);
    }

    @DisplayName("test distribution info")
    @Test
    public void testDistributionInfo() {

        Map<String, ?> result = (Map<String, ?>) score.call("distributionInfo", BigInteger.valueOf(1L));

        assertFalse((boolean) result.get("isValid"));

        result = (Map<String, ?>) score.call("distributionInfo", BigInteger.valueOf(0L));

        assertTrue((boolean) result.get("isValid"));
        assertEquals(ICX.multiply(BigInteger.valueOf(1_000_000)), result.get("distribution"));
        assertEquals(BigInteger.valueOf(1L), result.get("day"));

        sm.getBlock().increase(86400 / 2);//1

        result = (Map<String, ?>) score.call("distributionInfo", BigInteger.valueOf(1L));

        assertTrue((boolean) result.get("isValid"));
        assertEquals(ICX.multiply(BigInteger.valueOf(1_000_000)), result.get("distribution"));//2-1
        assertEquals(BigInteger.valueOf(2L), result.get("day"));//1+1

        sm.getBlock().increase(86400 * 4 / 2);//1+4 = 5
        result = (Map<String, ?>) score.call("distributionInfo", BigInteger.valueOf(2L));
        assertTrue((boolean) result.get("isValid"));
        assertEquals(BigInteger.valueOf(6L), result.get("day")); //5+1
        assertEquals(ICX.multiply(BigInteger.valueOf(1_000_000)).multiply(BigInteger.valueOf(4L)),
                result.get("distribution"));//6-2

        sm.getBlock().increase(86400 * 25 / 2);//5+25=30
        result = (Map<String, ?>) score.call("distributionInfo", BigInteger.valueOf(25L));
        assertTrue((boolean) result.get("isValid"));
        assertEquals(BigInteger.valueOf(31L), result.get("day")); //30+1=31
        //31-25 => 25+26+27+28+29+30
        //last 5 day of first month  (25+26+27+28+29 1M) 5days
        BigInteger total = ICX.multiply(BigInteger.valueOf(1_000_000)).multiply(BigInteger.valueOf(5L));
        //31st day (30 400k)
        total = total.add(ICX.multiply(BigInteger.valueOf(400_000)));
        assertEquals(total, result.get("distribution"));

    }

    private void addAsset(String type, Address address) {
        Object[] params = new Object[]{
                type, address, address.toString()
        };
        score.invoke(mockAddress.get(Contracts.REWARDS), "addAsset", params);
    }

    private void setAssetWeight(BigInteger timestamp, String typeId, Map<Address, Long> map) {
        WeightStruct[] weights = map.entrySet().stream().map(e -> {
            WeightStruct struct = new WeightStruct();
            struct.weight = BigInteger.valueOf(e.getValue()).multiply(ICX).divide(BigInteger.valueOf(100));
            struct.address = e.getKey();
            return struct;
        }).toArray(WeightStruct[]::new);

        Object[] params = new Object[]{typeId, weights, timestamp};

        score.invoke(owner, "setAssetWeight", params);
    }

    private void initAssetWeight(BigInteger timestamp, Integer typeId, Map<Address, Long> values) {
        String type = TYPE_ID_PREFIX + typeId;
        WeightStruct[] weights = values.entrySet().stream().map(entry -> {
            Address key = entry.getKey();
            Long value = entry.getValue();
            addAsset(type, key);
            WeightStruct struct = new WeightStruct();
            struct.weight = BigInteger.valueOf(value).multiply(ICX).divide(BigInteger.valueOf(100));
            struct.address = key;
            return struct;
        }).toArray(WeightStruct[]::new);

        Object[] params = new Object[]{type, weights, timestamp};

        score.invoke(owner, "setAssetWeight", params);
    }


    private void setTypeWeight(BigInteger timestamp, Map<Integer, Long> map) {
        TypeWeightStruct[] weights = map.entrySet().stream().map(e -> {
            TypeWeightStruct struct = new TypeWeightStruct();
            struct.weight = BigInteger.valueOf(e.getValue()).multiply(ICX).divide(BigInteger.valueOf(100));
            struct.key = "Key-" + e.getKey();
            return struct;
        }).toArray(TypeWeightStruct[]::new);

        Object[] params = new Object[]{weights, timestamp};

        score.invoke(owner, "setTypeWeight", params);
    }

    private void initTypeWeight(BigInteger timestamp, Long... values) {
        TypeWeightStruct[] weights = new TypeWeightStruct[values.length];
        IntStream.range(0, values.length)
                .forEach(idx -> {
                    addType(mockAddress.get(Contracts.REWARDS), "Key-" + (idx + 1));
                    TypeWeightStruct struct = new TypeWeightStruct();
                    struct.weight = BigInteger.valueOf(values[idx]).multiply(ICX).divide(BigInteger.valueOf(100));
                    struct.key = "Key-" + (idx + 1);
                    weights[idx] = struct;
                });

        Object[] params = new Object[]{weights, timestamp};

        score.invoke(owner, "setTypeWeight", params);
    }


    public void expectErrorMessage(Executable contractCall, String errorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(errorMessage, e.getMessage());
    }


    private BigInteger getTimestamp() {
        return BigInteger.valueOf(sm.getBlock().getTimestamp());
    }

}
