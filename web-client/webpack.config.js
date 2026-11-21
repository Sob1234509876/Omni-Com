const HtmlWebpackPlugin = require("html-webpack-plugin");

const projects = [{ name: "osa", version: "1.0a" }];

module.exports = {
  mode: "development",
  entry: (() => {
    var o = {};
    projects.forEach((v) => (o[v.name] = `./src/ts/${v.name}/page-loader.ts`));
    return o;
  })(),
  output: {
    filename: "[name]-[contenthash].js",
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [{ loader: "style-loader" }, { loader: "css-loader" }],
      },
      { test: /\.ts/, use: "ts-loader" },
      {
        test: /\.svg/,
        use: { loader: "file-loader", options: { name: "[name].[ext]" } },
      },
    ],
  },
  plugins: projects.map(
    (v) =>
      new HtmlWebpackPlugin({
        chunks: [v.name],
        favicon: "./src/assets/icon/oc.png",
        template: `src/template/${v.name}/index.html`,
      }),
  ),
  resolve: {
    extensions: [".ts"],
  },
};
