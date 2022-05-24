package finance.omm.core.score.interfaces;

import java.math.BigInteger;
import java.util.List;

import score.Address;
import score.annotation.Optional;

public interface WorkerToken {

    String name();
    String symbol();
    BigInteger decimals();
    BigInteger totalSupply();
    BigInteger balanceOf(Address _owner);
    void transfer(Address _to, BigInteger _value, @Optional byte[] _data);
    List<Address> getWallets();
}