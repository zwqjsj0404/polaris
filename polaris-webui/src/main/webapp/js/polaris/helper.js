goog.provide("polaris.helper");

goog.require("goog.string");

polaris.helper.convertJumpTargetToUrlFragment = function(jumpTarget) {
  return goog.string.format(
      "#page=code&file=%d&offset=%d",
      jumpTarget.fileId,
      jumpTarget.offset);
}

polaris.helper.dropPackageName = function(fullTypeName) {
  var pos = fullTypeName.lastIndexOf(".");
  return fullTypeName.substring(pos + 1);
}

polaris.helper.dropPackageAndClassName = function(fullMemberName) {
  var pos = fullMemberName.lastIndexOf("#");
  return fullMemberName.substring(pos + 1);
}

polaris.helper.codeOffsetToLineNum = function(code, offset) {
  if (offset >= code.length) {
    console.log("Offset " + offset + " is out of range");
    offset = code.length;
  }
  return goog.string.countOf(code.substring(0, offset), "\n");
}

