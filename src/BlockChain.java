import java.util.ArrayList;
import java.util.List;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
	public static final int CUT_OFF_AGE = 10;
	private TransactionPool txPool = new TransactionPool();
	private List<BlockNode> blockChain = new ArrayList<>();
	private BlockNode maxHeightNode;

	private void updateMaxHeightNode() {
		BlockNode currentMaxHeightNode = this.maxHeightNode;
		for (BlockNode blockNode : blockChain) {
			if (blockNode.height > currentMaxHeightNode.height) {
				currentMaxHeightNode = blockNode;
			} else if (blockNode.height == currentMaxHeightNode.height) {
				if (currentMaxHeightNode.createTime > (blockNode.createTime)) {
					currentMaxHeightNode = blockNode;
				}
			}
		}
		this.maxHeightNode = currentMaxHeightNode;
	}
	
	private BlockNode getParentNode(byte[] blockHash) {
		ByteArrayWrapper prevBlockHashBytes = new ByteArrayWrapper(blockHash);
		for (BlockNode blockNode : this.blockChain) {
			ByteArrayWrapper blockNodeHashBytes = new ByteArrayWrapper(blockNode.block.getHash());
			if (prevBlockHashBytes.equals(blockNodeHashBytes)) {
				return blockNode;
			}
		}
		return null;
	}
	
	class BlockNode {
		private Block block;
		private int height = 0;
		private UTXOPool utxoPool = new UTXOPool();
		private TransactionPool txPool = new TransactionPool();
		private long createTime;

		public BlockNode(Block block, int height, UTXOPool utxoPool, TransactionPool txPool) {
			this.block = block;
			this.height = height;
			this.utxoPool = utxoPool;
			this.txPool = txPool;
			this.createTime = System.currentTimeMillis();
		}
	}

	/**
	 * create an empty block chain with just a genesis block. Assume
	 * {@code genesisBlock} is a valid block
	 */
	public BlockChain(Block genesisBlock) {
		UTXOPool utxoPool = new UTXOPool();
		TransactionPool txPool = new TransactionPool();

		Transaction genesisBlockCoinbase = genesisBlock.getCoinbase();
		for (int i = 0; i < genesisBlockCoinbase.numOutputs(); i++) {
			utxoPool.addUTXO(new UTXO(genesisBlockCoinbase.getHash(), i), genesisBlockCoinbase.getOutput(i));
		}
		txPool.addTransaction(genesisBlockCoinbase);
		for (Transaction tx : genesisBlock.getTransactions()) {
			if (tx != null) {
				for (int i = 0; i < tx.numOutputs(); i++) {
					Transaction.Output output = tx.getOutput(i);
					UTXO utxo = new UTXO(tx.getHash(), i);
					utxoPool.addUTXO(utxo, output);
				}
				txPool.addTransaction(tx);
			}
		}
		BlockNode blockNode = new BlockNode(genesisBlock, 1, utxoPool, txPool);
		this.maxHeightNode = blockNode;
		blockChain.add(blockNode);
	}

	/** Get the maximum height block */
	public Block getMaxHeightBlock() {
		return this.maxHeightNode.block;
	}

	/** Get the UTXOPool for mining a new block on top of max height block */
	public UTXOPool getMaxHeightUTXOPool() {
		return this.maxHeightNode.utxoPool;
	}

	/** Get the transaction pool to mine a new block */
	public TransactionPool getTransactionPool() {
		return this.txPool;
	}

	/**
	 * Add {@code block} to the block chain if it is valid. For validity, all
	 * transactions should be valid and block should be at
	 * {@code height > (maxHeight - CUT_OFF_AGE)}.
	 * 
	 * <p>
	 * For example, you can try creating a new block over the genesis block (block
	 * height 2) if the block chain height is {@code <=
	 * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot
	 * create a new block at height 2.
	 * 
	 * @return true if block is successfully added
	 */
	public boolean addBlock(Block block) {
		if (block.getPrevBlockHash() == null) {
			return false;
		}
		BlockNode parentNode = getParentNode(block.getPrevBlockHash());
		if (parentNode == null) {
			return false;
		}

		int blockHeight = parentNode.height + 1;
		if (blockHeight <= maxHeightNode.height - CUT_OFF_AGE) {
			return false;
		}

		UTXOPool utxoPool = new UTXOPool(parentNode.utxoPool);
		TransactionPool txPool = new TransactionPool(parentNode.txPool);

		for (Transaction tx : block.getTransactions()) {
			TxHandler txHandler = new TxHandler(utxoPool);
			if (!txHandler.isValidTx(tx)) {
				return false;
			}

			for (Transaction.Input input : tx.getInputs()) {
				int outputIndex = input.outputIndex;
				byte[] prevTxHash = input.prevTxHash;

				UTXO utxo = new UTXO(prevTxHash, outputIndex);
				utxoPool.removeUTXO(utxo);
			}

			for (int i = 0; i < tx.numOutputs(); i++) {
				UTXO utxo = new UTXO(tx.getHash(), i);
				utxoPool.addUTXO(utxo, tx.getOutput(i));
			}
		}

		Transaction blockCoinbase = block.getCoinbase();
		for (int i = 0; i < blockCoinbase.numOutputs(); i++) {
			utxoPool.addUTXO(new UTXO(blockCoinbase.getHash(), i), blockCoinbase.getOutput(i));
		}

		for (Transaction tx : block.getTransactions()) {
			txPool.removeTransaction(tx.getHash());
		}

		BlockNode blockNode = new BlockNode(block, blockHeight, utxoPool, txPool);
		boolean addNewBlock = this.blockChain.add(blockNode);
		if (addNewBlock) {
			updateMaxHeightNode();
		}
		return addNewBlock;
	}

	/** Add a transaction to the transaction pool */
	public void addTransaction(Transaction tx) {
		this.txPool.addTransaction(tx);
	}
}