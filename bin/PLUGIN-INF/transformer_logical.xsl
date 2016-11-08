<?xml version="1.0" encoding="ISO-8859-1"?>
<!-- Edited by XMLSpy® -->
<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:template match="/">
   	<div class='menuWrap'>
    	<div class='pageContent'>
		<ul class='accordion'>
			<li>
				<xsl:for-each select="xml-fragment/div">
						<a><xsl:value-of select="@LABEL"/></a>
						<ul>
						<xsl:for-each select="div">
						<li><a><xsl:value-of select="@LABEL"/></a><div>
						<xsl:for-each select="div">
						<xsl:variable name="fileid">
						<xsl:value-of select="fptr/@FILEID"/>
						</xsl:variable>
						<a href='javascript:BookReader.prototype.menuBar({$fileid})'> <xsl:value-of select="@LABEL"/></a>

						</xsl:for-each>
						</div></li>
						</xsl:for-each>
						</ul>
				</xsl:for-each>
			</li>
		</ul>
		</div>
	</div>
</xsl:template>
</xsl:stylesheet>