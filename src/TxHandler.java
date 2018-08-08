import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
	UTXOPool utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
    	this.utxoPool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	Set<UTXO> utxoSet = new HashSet<UTXO>();
    	double inputValueSum = 0d;
    	
    	List<Transaction.Input> inputs = tx.getInputs();
    	for (int i = 0; i < inputs.size(); i++) {
    		Transaction.Input input = inputs.get(i);
    		UTXO lastUTXO = new UTXO(input.prevTxHash, input.outputIndex);
    		Transaction.Output prevTx = utxoPool.getTxOutput(lastUTXO);
    		
    		if (!utxoPool.contains(lastUTXO)) {
                return false;
            }
    		if(!Crypto.verifySignature(prevTx.address, tx.getRawDataToSign(i), input.signature)) {
    			return false;
    		}
    		utxoSet.add(lastUTXO);
    		inputValueSum += prevTx.value;
		}
    	
    	if (utxoSet.size() != tx.getInputs().size()) {
    		return false;
    	}
    	
    	double outputValueSum = 0d;
    	List<Transaction.Output> outputs = tx.getOutputs();
    	for (int i = 0; i < outputs.size(); i++) {
    		Transaction.Output output = outputs.get(i);
    		if (output.value < 0) {
    			return false;
    		}
    		outputValueSum += output.value;
		}
    	
    	if (inputValueSum < outputValueSum) {
    		return false;
    	}
    	
    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	List<Transaction> tempTx = new ArrayList<Transaction>();
    	for (int i = 0; i < possibleTxs.length; i++) {
			Transaction tx = possibleTxs[i];
			
			if (isValidTx(tx)) {
				tempTx.add(tx);
				
				List<Transaction.Input> inputs = tx.getInputs();
				for (int j = 0; j < inputs.size(); j++) {
					Transaction.Input input = inputs.get(j);
					UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
					utxoPool.removeUTXO(utxo);
				}
				
				List<Transaction.Output> outputs = tx.getOutputs();
				for (int j = 0; j < outputs.size(); j++) {
					Transaction.Output output = outputs.get(j);
					UTXO utxo = new UTXO(tx.getHash(), j);
					utxoPool.addUTXO(utxo, output);
				}
			}
		}
    	
    	Transaction[] acceptedTx = new Transaction[tempTx.size()];
    	return tempTx.toArray(acceptedTx);
    }
}
