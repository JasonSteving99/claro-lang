import data from './{{MODULE_DEP_GRAPH_CONFIG_JSON}}' assert { type: 'json' };

export default {
  define: {
    __MODULE_DEP_GRAPH_CONFIG__: JSON.stringify(data),
  },
}