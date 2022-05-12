package finance.omm.score.tokens;

import java.math.BigInteger;

import finance.omm.libs.address.AddressProvider;
import finance.omm.libs.address.Contracts;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import static finance.omm.utils.math.MathUtils.*;

public class DTokenImpl extends AddressProvider {

    /*
    Implementation of IRC2
    */
    private static final String NAME = "token_name";
    private static final String SYMBOL = "token_symbol";
    private static final String DECIMALS = "decimals";
    private static final String TOTAL_SUPPLY = "total_supply";
    private static final String BALANCES = "balances";
    private static final String USER_INDEXES = "user_indexes";
    private static final BigInteger ZERO = BigInteger.valueOf(0);

    private final VarDB<String> name = Context.newVarDB(NAME, String.class);
    private final VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    private final VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    private final VarDB<BigInteger> totalSupply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);
    private final DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);
    private final DictDB<Address, BigInteger> userIndexes = Context.newDictDB(USER_INDEXES, BigInteger.class);

    /**
    Variable Initialization.
    @param _addressProvider: the address of addressProvider
    @param _name: The name of the token.
    @param _symbol: The symbol of the token.
    @param _decimals: The number of decimals. Set to 18 by default.
    */
    public DTokenImpl(Address addressProvider, String _name, String _symbol, BigInteger _decimals, boolean _update) {
        super(addressProvider, _update);

        if (_symbol.isEmpty()) {
            Context.revert("Invalid Symbol name");
        }

        if (_name.isEmpty()) {
            Context.revert("Invalid Token Name");
        }

        if (_decimals.compareTo(ZERO) < 0) {
            Context.revert("Decimals cannot be less than zero");
        }

        this.name.set(_name);
        this.symbol.set(_symbol);
        this.decimals.set(_decimals);
        this.totalSupply.set(ZERO);
    }

    public void onUpdate() {

    }

    private BigInteger calculateCumulatedBalanceInternal(Address user, BigInteger balance) {
        BigInteger userIndex = this.userIndexes.getOrDefault(user, ZERO);
        if (userIndex.equals(ZERO)) {
            return balance;
        } else {
            BigInteger decimals = this.decimals.getOrDefault(ZERO);
            BigInteger newBalance = exaDivide(
                    exaMultiply(
                            convertToExa(balance, decimals),
                            Context.call(
                                    BigInteger.class,
                                    this._addresses.get(Contracts.LENDING_POOL_CORE.getKey()),
                                    "getNormalizedDebt",
                                    this._addresses.get(Contracts.RESERVE.getKey()))
                            )
                    ,userIndex);
            return convertExaToOther(newBalance, decimals.intValue());
        }
    }
}
