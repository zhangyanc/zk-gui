package pers.zyc.zkgui;

/**
 * @author zhangyancheng
 */
@SuppressWarnings("unused")
public interface Bridge {

	Object connect(String connectString) throws Exception;

	void quit();

	Object getNodeInfo(String node) throws Exception;

	Object createNode(String node, String data, boolean ephemeral, boolean sequential) throws Exception;

	Object setData(String node, String data) throws Exception;

	Object deleteNode(String node) throws Exception;
}
