package it.unive.lisa.outputs.serializableGraph;

public class SerializableNodeDescription implements Comparable<SerializableNodeDescription> {

	private int nodeId;

	private SerializableValue description;

	public SerializableNodeDescription() {
	}

	public SerializableNodeDescription(int id, SerializableValue description) {
		this.nodeId = id;
		this.description = description;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public SerializableValue getDescription() {
		return description;
	}

	public void setDescription(SerializableValue description) {
		this.description = description;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + nodeId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SerializableNodeDescription other = (SerializableNodeDescription) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (nodeId != other.nodeId)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "JsonNodeDescription [nodeId=" + nodeId + ", description=" + description + "]";
	}

	@Override
	public int compareTo(SerializableNodeDescription o) {
		int cmp;
		if ((cmp = Integer.compare(nodeId, o.nodeId)) != 0)
			return cmp;
		return description.compareTo(o.description);
	}

}
