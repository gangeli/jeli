#!/usr/bin/env ruby

require 'set'

require 'sdl'
require 'opengl'
require 'RMagick'
include Gl,Glu


#(user movement scaling constants)
TRANSFORM_SCALE = 0.01
ROTATE_SCALE = 1.0
#(pixels from edge of screen to jump to middle)
MOUSE_BOUNDARY = 10
#(number of frames to count a movement for)
ANTIALIAS_SECONDS = 0.1
JUMP_HEIGHT = 1.5
JUMP_SECONDS = 0.5

## -----------------------------------------------------------------------------
## Utilities
## -----------------------------------------------------------------------------
class Point3D
	attr_accessor :x, :y, :z, :alpha
	def initialize(x=0.0,y=0.0,z=0.0,alpha=1.0)
		@x = x
		@y = y
		@z = z
		@alpha=alpha
	end
end
class Vector3D < Point3D
end
class Color
	attr_accessor :red, :green, :blue, :alpha
	def initialize(red=0.0,green=0.0,blue=0.0,alpha=0.0)
		@red = red
		@green = green
		@blue = blue
		@alpha = alpha
	end
end

## -----------------------------------------------------------------------------
## Drawable Class
## -----------------------------------------------------------------------------
class Drawable
	def initialize(parent=nil)
		@parent = parent
	end

	def draw(window)
		#--Draw Parent(s)
		if @parent != nil then @parent.draw end
		#--Draw This
		#(save state)
		glPushAttrib (GL_ALL_ATTRIB_BITS)
		glPushMatrix
		#(object properties)
		if(@color != nil) then
			glColor4f(@color.red, @color.green, @color.blue, @color.alpha)
		end
		if(@offset != nil) then
			glTranslatef(@offset[0],@offset[1],@offset[2])
		end
		if(@rotate != nil) then
			glRotatef(@rotate[0],1.0,0.0,0.0)
			glRotatef(@rotate[1],0.0,1.0,0.0)
			glRotatef(@rotate[2],0.0,0.0,1.0)
		end
		if(@normal != nil) then
			glNormal3f(@normal[0],@normal[1],@normal[2])
		end
		if(@texture != nil) then
			glEnable(GL_TEXTURE_2D)
			glBindTexture(GL_TEXTURE_2D,@texture)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
		end
		#(render native GL)
		renderGL(window)
		#(cleanup)
		if(@texture != nil)
			glDisable(GL_TEXTURE_2D)
		end
		#(restore state)
		glPopAttrib
		glPopMatrix
	end

	def renderGL(window)
		puts "WARNING: raw Drawable cannot be rendered"
	end

	#---------------
	# Modifiers
	#---------------
	def level(level)
		@level = level
	end
	def getLevel; if @level == nil then 0.0 else @level end end
	def color(r=0.0,g=0.0,b=0.0,a=1.0) 
		if(r.kind_of? Color) then @color = r
		else @color = Color.new(r,g,b,a)
		end
		self
	end
	def textureData(data, width, height,tileFactor=1.0) 
		#(error check)
		if @prohibitTexture then
			raise "Textures are not permitted on this drawable"
		end
		def power2(i)
			if i == 1 then return true 
			elsif 2*(i/2) != i then return false
			else return power2(i/2)
			end
		end
		if width != height or !power2(width) or !power2(height) then
			raise "Texture image must be a square and power of two (#{width},#{height})"
		end
		#(Create texture)
		@texture = glGenTextures(1)[0]
		glBindTexture(GL_TEXTURE_2D,@texture)
		glTexImage2D(
			GL_TEXTURE_2D,		# target
			0,					# mipmap level
			GL_RGB,				# internal format
			width, height,		# dims
			0,					# no border
			GL_RGB,				# components per pixel
			GL_UNSIGNED_BYTE,	# component type
			data 				# packed data
		)
		@textureFactor = tileFactor
		self
	end
	def texture(path,tileFactor=1.0) 
		data = Drawable.img2data(path)
		img = Magick::ImageList.new(path)
		textureData(data, img.columns, img.rows, tileFactor)
		self
	end
	def offset(x=0.0,y=0.0,z=0.0) @offset = [x,y,z]; self end
	def rotate(x=0.0,y=0.0,z=0.0) @rotate = [x,y,z]; self end
	def normal(x=0.0,y=0.0,z=0.0) @normal = [x,y,z]; self end
	
	#---------------
	# Static Methods
	#---------------
	def self.img2data(path,x=0.0,y=0.0,columns=nil,rows=nil)
		Magick::ImageList.new(path)
		pixels = if columns != nil and rows != nil then
			Magick::ImageList.new(path).first.export_pixels_to_str(x,y,columns,rows)
		else
			Magick::ImageList.new(path).first.export_pixels_to_str(x,y)
		end
		pixels
	end

end

## -----------------------------------------------------------------------------
## Drawables
## -----------------------------------------------------------------------------
class Group < Drawable
	def initialize(*members)
		@members = members - [nil]
	end
	def renderGL(window)
		@members.each do |drawable|
			drawable.draw(window)
		end
	end
	#---Special Overrides---
	def textureData(data, width, height,tileFactor) 
		@members.each do |drawable| 
			drawable.textureData(data, width, height,tileFactor) 
		end
	end
end

class Sphere < Drawable
	@@quadric = nil
	def initialize(radius=1.0, resolution=32)
		@radius = radius
		@resolution = resolution
		if not @@quadric then
			@@quadric = gluNewQuadric
			gluQuadricNormals( @@quadric, GLU_SMOOTH )
			gluQuadricTexture( @@quadric, GLU_TRUE )
		end
	end
	def radius(radius); @radius = radius; self; end
	def resolution(res); @resolution = res; self; end
	def renderGL(window)
		gluSphere( @@quadric, @radius, @resolution, @resolution)
	end
end

class Cube < Drawable
	def initialize(origin=[0,0,0],vector=[1,1,1],alpha=1.0)
		@alpha = alpha
		#(set origin)
		if origin.kind_of? Point3D then
			@origin=[origin.x,origin.y,origin.z]
		elsif origin.kind_of? Array then
			@origin=origin
		else
			raise "Cube takes 2 arguments: [origin point], [diagonal vector]"
		end
		#(set vector)
		if vector.kind_of? Point3D then
			@vector=[vector.x,vector.y,vector.z]
		elsif origin.kind_of? Array then
			@vector=vector
		else
			raise "Cube takes 2 arguments: [origin point], [diagonal vector]"
		end
		#compile the cube
		compile
	end
	def renderGL(window)
		glBegin(GL_QUADS)
			@faces.each do |normal,*face|
				glNormal3f(normal[0],normal[1],normal[2])
				if @texture then glTexCoord2f(@textureFactor,@textureFactor) end
				glVertex4f(face[0][0],face[0][1],face[0][2],@alpha)
				if @texture then glTexCoord2f(0.0,@textureFactor) end
				glVertex4f(face[1][0],face[1][1],face[1][2],@alpha)
				if @texture then glTexCoord2f(0.0,0.0) end
				glVertex4f(face[2][0],face[2][1],face[2][2],@alpha)
				if @texture then glTexCoord2f(@textureFactor,0.0) end
				glVertex4f(face[3][0],face[3][1],face[3][2],@alpha)
			end
		glEnd
	end

	def compile
		def normal(x,y,z)
			[x,y,z]
		end
		def shift(x,y,z)
			[
			if x then @origin[0] + @vector[0] else @origin[0] end,
			if y then @origin[1] + @vector[1] else @origin[1] end,
			if z then @origin[2] + @vector[2] else @origin[2] end,
			]
		end
		#note: comments for origin=(0,0,0) vector=(1,1,1)
		@faces = [
			#(face 1: 'back')
			[normal(0,0,-1),
				shift(true,false,false),
				shift(false,false,false),	#origin
				shift(false,true,false),
				shift(true,true,false)
			#(face 2: 'right')
			],[normal(1,0,0),
				shift(true,false,true),
				shift(true,false,false),
				shift(true,true,false),
				shift(true,true,true)		#vector
			#(face 3: 'front')
			],[normal(0,0,1),
				shift(false,false,true),
				shift(true,false,true),
				shift(true,true,true),		#vector
				shift(false,true,true)
			#(face 4: 'left')
			],[normal(-1,0,0),
				shift(false,false,false),	#origin
				shift(false,false,true),
				shift(false,true,true),
				shift(false,true,false)
			#(face 5: 'top')
			],[normal(0,1,0),
				shift(false,true,true),
				shift(true,true,true),		#vector
				shift(true,true,false),
				shift(false,true,false)
			#(face 6: 'bottom')
			],[normal(0,-1,0),
				shift(false,false,false),	#origin
				shift(false,false,true),
				shift(true,false,true),
				shift(true,false,false)
			]
		]
	end
	private :compile

	def normal
		raise "Cube has pre-defined normals"
	end
	def vector(xdir,ydir,zdir)
		@vector=[xdir,ydir,zdir]; compile; self
	end
	def xLength(xdir)
		@vector = [xdir,@vector[1],@vector[2]]; compile; self
	end
	def yLength(ydir)
		@vector = [@vector[0],ydir,@vector[2]]; compile; self
	end
	def zLength(zdir)
		@vector = [@vector[0],@vector[1],zdir]; compile; self
	end

	def origin(x=0,y=0,z=0)
		@origin = [x,y,z]; compile; self
	end
end

class Polygon < Drawable
	def initialize(points,colors=nil)
		#(points)
		@points = points - [nil]
		#(colors)
		if(colors != nil and @points.lenth != (colors - [nil]).length) then
			raise ArgumentError.new("Colors and Points are not the same size")
		end
		if(colors != nil) then @colors = colors - [nil] end
		#(textures)
		if points.length < 3 or points.length > 4 then
			@prohibitTexture = true
		elsif points.length == 3 then
			@texFn = [
				lambda{ glTexCoord2f(@textureFactor,@textureFactor) },
				lambda{ glTexCoord2f(@textureFactor/2.0,0.0) },
				lambda{ glTexCoord2f(0.0,@textureFactor) },
			nil] 
		elsif points.length == 4 then
			@texFn = [
				lambda{ glTexCoord2f(@textureFactor,@textureFactor) },
				lambda{ glTexCoord2f(0.0,@textureFactor) },
				lambda{ glTexCoord2f(0.0,0.0) },
				lambda{ glTexCoord2f(@textureFactor,0.0) },
			nil] 
		end
			
	end
	def self.fromPoints(*points)
		rtn = []
		points.each do |p|
			if p != nil then
				if(p.length == 4) then
					rtn << Point3D.new(p[0],p[1],p[2],p[3])
				else
					rtn << Point3D.new(p[0],p[1],p[2])
				end
			end
		end
		Polygon.new(rtn)
	end
	def self.rectangle(width=1,height=1)
		Polygon.new([
			Point3D.new(0,0,0),
			Point3D.new(width,0,0),
			Point3D.new(width,height,0),
			Point3D.new(0,height,0)
		])
	end
	def renderGL(window)
		glBegin(GL_POLYGON)
			@points.each_with_index do |p, i|
				#(color)
				if(@colors != nil) then
					c = @colors[i]
					glColor4f(c.red, c.green, c.blue, c.alpha);
				end
				#(texture)
				if !@prohibitTexture and @texture != nil then
					@texFn[i].call
				end
				#(vertex)
				glVertex4f(p.x,p.y,p.z,p.alpha)
			end
		glEnd
	end
end

class Text < Drawable
	@@font = nil
	@@base = nil
	def initialize(text,row=0,col=0)
		@text = text
		@x=col*10; @y=row*16
		if(@@font == nil) then
			Text.init
		end
		level(1.0)
	end

	def bold
		@bold = true
		self
	end
	def text(text)
		@text = text
		self
	end
	def move(row, col)
		@x=col*10; @y=row*16
		self
	end

	def self.init
		#--Get Font Data
		#(get data)
		@@font = glGenTextures(1)[0]
		glEnable( GL_TEXTURE_2D )
		glBindTexture(GL_TEXTURE_2D,@@font)
		data = Drawable.img2data("font.bmp")
		#(create texture)
		glTexImage2D(
			GL_TEXTURE_2D,		# target
			0,					# mipmap level
			GL_RGB,				# internal format
			256, 256,			# dims (fixed for font)
			0,					# no border
			GL_RGB,				# components per pixel
			GL_UNSIGNED_BYTE,	# component type
			data 				# packed data
		)
		#--Build Font
		@@base = glGenLists(256)
		for i in 0...256 do
			cx = 1.0-((i%16).to_f/16.0)
			cy = 1.0-((i/16).to_f/16.0)
			glNewList( @@base + (255 - i), GL_COMPILE)
				glBegin( GL_QUADS )
					#(coordinates of character)
					glTexCoord2f( cx - 0.0625, cy) # bottom left
					glVertex2i(0,0)
					glTexCoord2f(cx,cy) # bottom right
					glVertex2i(16,0)
					glTexCoord2f(cx, cy-0.0625) #top right
					glVertex2i(16,16)
					glTexCoord2f(cx-0.0625, cy-0.0625) #top left
					glVertex2i(0,16)
				glEnd
				glTranslated( 10, 0, 0)
			glEndList
		end
	end

	def renderGL(window)
		@x = @x % window.width
		@y = @y % window.height
		#--Render Text
		glEnable(GL_BLEND)
		glEnable(GL_TEXTURE_2D)
		glBindTexture(GL_TEXTURE_2D, @@font)
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
		glDisable(GL_DEPTH_TEST)
		glMatrixMode(GL_PROJECTION)
		glPushMatrix
			glLoadIdentity
			glOrtho(0,window.width,0,window.height,-1,1)
			glMatrixMode(GL_MODELVIEW)
			glPushMatrix
				glLoadIdentity
				glTranslated( @x, window.height - 16 - @y, 0)
				shift = @@base -32 + if @bold then 128 else 0 end
				glCallLists( GL_UNSIGNED_BYTE, 
					@text.unpack("C*").map{|c|c+shift}.pack("C*") )
				glMatrixMode( GL_PROJECTION )
			glPopMatrix
			glMatrixMode( GL_MODELVIEW )
		glPopMatrix
		glEnable(GL_DEPTH_TEST)
		glDisable(GL_BLEND)
		glDisable(GL_TEXTURE_2D)
	end
end


## -----------------------------------------------------------------------------
## Modules
## -----------------------------------------------------------------------------
class Module
	def onInit(window)
		puts "WARNING: onInit called on raw Module"
	end
	def onDestroy(window)
		puts "WARNING: onDestroy called on raw Module"
	end
	def onRender(window,level)
		puts "WARNING: onRender called on raw Module"
	end
	def requestLevels; [0.0]; end
end

class Lighting < Module
	@@exists = false

	def initialize(amb=1.0)
		#(ensure singleton instance)
		if @@exists then
			raise "Cannot create multiple Lighting modules"
		end
		@@exists = true
		#(initialize)
		ambient(amb)
		@nextLight = 0
		@lights = {}
	end

	def ambient(ambient)
		if ambient.kind_of? Float then
			@ambient = [ambient,ambient,ambient,1.0]
		else
			@ambient = ambient
		end
		self
	end

	def indexToLight(index)
		case index
		when 0 then GL_LIGHT0
		when 1 then GL_LIGHT1
		when 2 then GL_LIGHT2
		when 3 then GL_LIGHT3
		when 4 then GL_LIGHT4
		when 5 then GL_LIGHT5
		when 6 then GL_LIGHT6
		when 7 then GL_LIGHT7
		else raise "Invalid light index (too many lights?)!"
		end
	end
	private :indexToLight


	def light(pos=Point3D.new,color=Color.new,diffuse=false,rel=false)
		#(get the light)
		light = indexToLight(@nextLight)
		@lights[@nextLight] = [pos,diffuse]
		@nextLight += 1
		#(render the light)
		glEnable(light)
		glLightfv(light, GL_DIFFUSE, 
			[color.red,color.green,color.blue,color.alpha])
		glLightfv(light, GL_POSITION, 
			[pos.x, pos.y, pos.z, if diffuse then 1.0 else 0.0 end])
		#(relative vs. absolute)
		if rel then relative else absolute end
		self
	end

	def absolute(light=@nextLight-1)
		if light<0 or light>=@nextLight then raise "Light doesn't exist!" end
		if !@fixedLights then @fixedLights = [].to_set end
		@fixedLights << light
		self
	end

	def relative(light=@nextLight-1)
		if light<0 or light>=@nextLight then raise "Light doesn't exist!" end
		if !@fixedLights then @fixedLights = [].to_set end
		@fixedLights.delete(light)
		self
	end

	def onInit(window)
		if not @initialized then
			glEnable(GL_LIGHTING)
			glEnable(GL_NORMALIZE)
			@initialized = true
		end
	end

	def onDestroy(window)
		glDisable(GL_LIGHTING)
		glDisable(GL_NORMALIZE)
		glDisable(GL_LIGHT0)
		glDisable(GL_LIGHT1)
		glDisable(GL_LIGHT2)
		glDisable(GL_LIGHT3)
		glDisable(GL_LIGHT4)
		glDisable(GL_LIGHT5)
		glDisable(GL_LIGHT6)
		glDisable(GL_LIGHT7)
		@initialized = false
	end

	def onRender(window,level)
		if @fixedLights and level < GameKeys.level
			@fixedLights.each{ |light|
				pos,diffuse = @lights[light]
				glLightfv(indexToLight(light), GL_POSITION, 
					[pos.x, pos.y, pos.z, if diffuse then 1.0 else 0.0 end])
			}
		end
	end

	def getLevels; [GameKeys.level-1.0, GameKeys.level+1.0]; end
end

class GameKeys < Module
	TRANSFORM_SCALE = 0.03
	ROTATE_SCALE = 0.5
	def self.level; 0.0; end
	def onInit(window)
		#--Init Variables
		@xdir = 0
		@zdir = 0
		@rotateBuffer = []
		@jumpCounter = -1
		#--Change Settings
		SDL::WM::grabInput(SDL::WM::GRAB_ON)
		SDL::Mouse.hide
		SDL::Mouse.warp(window.width / 2, window.height / 2)
		#--Change Keys
		window.mouseButtonDown = lambda { |event|  }
		window.mouseButtonUp = lambda { |event| }
		validMiddlePass = false
		isWarp = lambda { |x,y|
				x == window.width / 2 and 
				y == window.height / 2 and 
				!validMiddlePass
		}
		window.mouseMotion = lambda { |event| 
			if window.visible then
				#(adjust for mouse warping)
				warp = isWarp.call(event.x,event.y)
				if (event.x == window.width / 2 and 
						event.y == window.height / 2) then
					validMiddlePass = true
				else
					validMiddlePass = false
				end
				#(update mouse)
				lastMouse = @mouse
				@mouse = [event.x, event.y]
				#(parse mouse events)
				if lastMouse != nil and !warp then
					antialiasFrames = ANTIALIAS_SECONDS*window.fps
					#(get spread out rotation)
					xrot = (@mouse[1] - lastMouse[1]).to_f / antialiasFrames
					yrot = (@mouse[0] - lastMouse[0]).to_f / antialiasFrames
					zrot = 0.0
					#(agregate rotations)
					newBuffer = []
					for i in 1..[antialiasFrames,@rotateBuffer.length].max do
						existing = @rotateBuffer.shift
						if i < antialiasFrames then
							if existing == nil then existing = [0.0,0.0,0.0] end
							newBuffer << 
							[xrot+existing[0],yrot+existing[1],zrot+existing[2]]
						else newBuffer << existing
						end
					end
					@rotateBuffer = newBuffer

				else #do nothing
				end
				#(adjust for edge of screen)
				if event.x < MOUSE_BOUNDARY or
						event.x > window.width - MOUSE_BOUNDARY or
						event.y < MOUSE_BOUNDARY or
						event.y > window.width - MOUSE_BOUNDARY then
					SDL::Mouse.warp(window.width / 2, window.height / 2)
				end
			end
		}
		window.keyDown = lambda { |event| 
			case event.sym
			when 119 then #forward
				@zdir = -1
			when 100 then #right
				@xdir = 1
			when 115 then #down
				@zdir = 1
			when 97  then #left
				@xdir = -1
			when 27 then #esc
			when 32 then #space
				if @jumpCounter < 0 then 
					@jumpBase = window.cameraPos[1]
					frames = JUMP_SECONDS*window.fps
					@jumpCounter = frames.to_i
					@jumpTotal = frames.to_i
				end
			else puts "Key pressed: #{event.sym}"
			end
		}
		window.keyUp = lambda { |event|
			case event.sym
			when 119 then #forward
				@zdir = 0 if @zdir == -1
			when 100 then #right
				@xdir = 0 if @xdir == 1
			when 115 then #down
				@zdir = 0 if @zdir == 1
			when 97  then #left
				@xdir = 0 if @xdir == -1
			when 27 then #esc
				window.kill
			when 32 then #space
			else puts "Key released: #{event.sym}"
			end
		}
	end
	def onDestroy(window)
		window.defaultEvents
	end
	def onRender(window,level)
		#(rotate)
		rot = @rotateBuffer.shift
		if rot != nil then
			window.rotateCamera(
				rot[0],
				rot[1],
				rot[2]
			)
		end
		#(scale)
		if @xdir != 0 or @zdir != 0 then
			window.moveCameraRel(
				TRANSFORM_SCALE*@xdir,
				0.0,
				TRANSFORM_SCALE*@zdir,
				true
			)
		end
		#(jump)
		if @jumpCounter >= 0 then
			x = (@jumpTotal-@jumpCounter).to_f
			term = 2.0*x/@jumpTotal.to_f - 1.0
			ypos = -(term*term) + 1.0
			window.setCameraY(-JUMP_HEIGHT.to_f*ypos+@jumpBase)
			@jumpCounter -= 1
		end
	end

	def getLevels; [GameKeys.level]; end
end

class InfoBar < Module
	def self.level; -10.0; end
	def onInit(window)
		text = ""
		@drawable = Text.new(text, -1, -text.length-1)
		window.draw(@drawable)
		@lastTick = Time.now-0.95
	end
	def onDestroy(window)
		window.erase(@drawable)
	end
	def onRender(window,level)
		tick = Time.now
		if tick - @lastTick > 1.0 then
			@lastTick = tick
			text = "#{window.fps.floor} Frames per second"
			@drawable.text(text).move(-1,-text.length-1)
		end
	end
	def getLevels; [InfoBar.level]; end
end

## -----------------------------------------------------------------------------
## Window Class
## -----------------------------------------------------------------------------
class Window
	attr_accessor :width, :height, :visible, :fps,
		#mouse keys
		:videoExpose, :quit, :active, :mouseButtonDown, :mouseButtonUp,
		:mouseMotion, :keyDown, :keyUp,
		#state
		:cameraPos, :cameraAngle, :sceneAngle

	#---------------
	# Initialization
	#---------------
	
	def initialize(width, height, title="OpenGL View")
		#--Variables
		#(properties)
		@width = width
		@height = height
		#(state)
		@visible = false
		@looping = true
		@mousedown = false
		@sceneAngle = [0.0,0.0,0.0]
		@cameraAngle = [0.0,0.0,0.0]
		@cameraPos = [0.0,0.0,0.0]
		@bgColor = [0.0,0.0,0.0,0.0]
		#(additions)
		@toDraw = []
		@mods = {}
		#--Initialize
		#(key events)
		defaultEvents
		#(init sdl)
		SDL.init(SDL::INIT_VIDEO)
		#SDL.setGLAttr(SDL::GL_DOUBLEBUFFER,1)
		#(create window)
		@surface = SDL.setVideoMode(@width,@height,32,SDL::OPENGL)
		SDL::WM::setCaption(title, title)
		#(init gl)
		glViewport(0,0,@width, @height)
		glMatrixMode(GL_PROJECTION)	#switch to setting camera perspective
		glLoadIdentity()			#reset the camera
		gluPerspective(
			45.0,					#camera angle
			@width / @height,		#width-to-height ratio
			0.5,					#near z clipping coordinate
			200.0					#far z clipping coordinate
			)
		glEnable(GL_DEPTH_TEST)
		glDepthFunc(GL_LEQUAL)
		glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST)
		glEnable(GL_COLOR_MATERIAL)
		glBlendFunc(GL_SRC_ALPHA, GL_ONE)
	end

	def onShow()
		@visible = true
	end

	#---------------
	# Set Scene
	#---------------
	
	def rotate(v,axis,rads)
		def degrees(rad)
			rad * Math::PI/180.0	
		end
		x=axis[0]
		y=axis[1]
		z=axis[2]
		c=Math.cos(degrees(rads))
		s=Math.sin(degrees(rads))
		t=1-c
		[
			v[0]*(t*x*x+c) + v[1]*(t*x*y+s*z) + v[2]*(t*x*z-s*y),
			v[0]*(t*x*y-s*z) + v[1]*(t*y*y+c) + v[2]*(t*y*z+s*x),
			v[0]*(t*x*y+s*y) + v[1]*(t*y*z-s*x) + v[2]*(t*z*z+c)
		]
	end

	def moveCameraAbs(x=0, y=0, z=0)
		setCameraPos(@cameraPos[0]-x,@cameraPos[1]-y,@cameraPos[2]-z)
		self
	end
	def moveCameraRel(x=0, y=0, z=0, forceGround=false)
		v = 
			rotate(
				rotate(
					rotate(
						[x,y,z],
						[1,0,0],
						@cameraAngle[0]),
					[0,1,0], 
					@cameraAngle[1]),
				[0,0,1],
				@cameraAngle[2])
		moveCameraAbs(v[0],if not forceGround then v[1] else 0.0 end, v[2])
		self
	end
	def setCameraPos(x=@cameraPos[0], y=@cameraPos[1], z=@cameraPos[2])
		@cameraPos = [x,y,z]
		self
	end
	def setCameraX(x=0)
		@cameraPos = [x, @cameraPos[1], @cameraPos[2]]
		self
	end
	def setCameraY(y=0)
		@cameraPos = [@cameraPos[0], y, @cameraPos[2]]
		self
	end
	def setCameraZ(z=0)
		@cameraPos = [@cameraPos[0], @cameraPos[1], z]
		self
	end
	def rotateScene(x=0, y=0, z=0)
		setSceneRotation(
			@sceneAngle[0]+x,
			@sceneAngle[1]+y,
			@sceneAngle[2]+z
			)
		self
	end
	def setSceneRotation(
			x=@sceneAngle[0], 
			y=@sceneAngle[1], 
			z=@sceneAngle[2]
			)
		@sceneAngle = [x,y,z]
		self
	end
	def rotateCamera(x=0, y=0, z=0, forceForward=false)
		setCameraRotation(
			@cameraAngle[0]+x,
			@cameraAngle[1]+y,
			@cameraAngle[2]+z,
			forceForward
			)
		self
		
	end
	def setCameraRotation(
			x=@cameraAngle[0],
			y=@cameraAngle[1],
			z=@cameraAngle[2],
			forceForward=false
			)
		if(x > 90) then x = 90 end
		if(x < -90) then x = -90 end
		@cameraAngle = [x,y,z]
		self
	end
	def setBackgroundColor(r=0.0,g=0.0,b=0.0,a=1.0)
		@bgColor=[r,g,b,a]
		self
	end

	#---------------
	# Plugins
	#---------------
	
	def addModule(mod)
		if not mod == nil then
			#(error check)
			if not mod.kind_of? Module then
				raise ArgumentError.new("Not a module!")
			end
			#(initialize module)
			mod.onInit(self)
			#(update module list)
			mod.getLevels.each{ |level|
				if !@mods[level] then @mods[level] = [] end
				@mods[level] << mod
			}
			#(update levels cache)
			@sortedLevelCache = @mods.keys.sort
		end
		self
	end

	def removeModule(mod)
		if not mod == nil then
			#(error check)
			if not mod.kind_of? Module then
				raise ArgumentError.new("Not a module!")
			end
			#(let module clean up)
			mod.onDestroy(self)
			#(update module list)
			@mods.each{ |level|
				@mods[level].delete(mod)
				if @mods[level].empty? then @mods.delete(level) end
			}
			#(update levels cache)
			@sortedLevelCache = @mods.keys.sort
		end
		self
	end

	def draw(drawable)
		if not drawable == nil
			if not drawable.kind_of? Drawable then
				raise ArgumentError.new("Cannot draw a non-drawable!")
			end
			@toDraw << drawable
		end
		@toDraw.sort!{ |a,b| a.getLevel <=> b.getLevel }
		self
	end

	def erase(drawable)
		@toDraw = @toDraw - [drawable]
		self
	end
	
	#---------------
	# Controls
	#---------------
	
	def defaultEvents
		@videoExpose = lambda { |event| onShow() }
		@quit = lambda { |event| kill() }
		@active = lambda { |event| }
		@mouseButtonDown = lambda { |event| @mousedown = true }
		@mouseButtonUp = lambda { |event| @mousedown = false }
		@mouseMotion = lambda { |event| 
			#(get the new mouse position)
			lastMouse = @mouse
			@mouse = [event.x, event.y]
			#(parse mouse events)
			if lastMouse != nil and @mousedown then
				case event.state
				#(case: left button move)
				when SDL::Mouse::BUTTON_LEFT then
					moveCameraAbs(
						TRANSFORM_SCALE*(lastMouse[0]-@mouse[0]),
						TRANSFORM_SCALE*(mouse[1]-lastMouse[1]),
						0.0
						)
				#(case: right button move)
				when 4 then
				#when SDL::Mouse::BUTTON_RIGHT then #TODO this should work
					rotateScene(
						ROTATE_SCALE*(@mouse[1]-lastMouse[1]),
						ROTATE_SCALE*(@mouse[0]-lastMouse[0]),
						0.0
						)
				else puts "Unhandled mouse click on button #{event.state}"
				end
			else #do nothing
			end
		}
		@keyDown = lambda { |event| }
		@keyUp = lambda { |event| }
	end

	def onEvent(event)
		case event
		when SDL::Event2::VideoExpose		then @videoExpose.call(event)
		when SDL::Event2::Quit				then @quit.call(event)
		when SDL::Event2::Active			then @active.call(event)
		when SDL::Event2::MouseButtonDown	then @mouseButtonDown.call(event)
		when SDL::Event2::MouseButtonUp		then @mouseButtonUp.call(event)
		when SDL::Event2::MouseMotion		then @mouseMotion.call(event)
		when SDL::Event2::KeyDown			then @keyDown.call(event)
		when SDL::Event2::KeyUp				then @keyUp.call(event)
		else puts "Unhandled event of type #{event}"
		end
	end

	def render()
		#--Render State
		#(clear state)
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
		glMatrixMode(GL_MODELVIEW)
		glLoadIdentity
		#(rotate camera)
		glRotatef(@cameraAngle[0],1.0,0.0,0.0)
		glRotatef(@cameraAngle[1],0.0,1.0,0.0)
		glRotatef(@cameraAngle[2],0.0,0.0,1.0)
		#(move camera)
		glTranslatef(@cameraPos[0], @cameraPos[1], @cameraPos[2])
		#(rotate scene)
		glRotatef(@sceneAngle[0],1.0,0.0,0.0)
		glRotatef(@sceneAngle[1],0.0,1.0,0.0)
		glRotatef(@sceneAngle[2],0.0,0.0,1.0)
		#(background color)
		glClearColor(@bgColor[0], @bgColor[1], @bgColor[2], @bgColor[3])

		#--Render Modules
		@sortedLevelCache.each do |level|
			@mods[level].each{ |mod|
				mod.onRender(self,level)
			}
		end

		#--Render Drawables
		@toDraw.each do |drawable|
			drawable.draw(self)
		end

		#--Finalize
		SDL.GLSwapBuffers()
	end

	def kill()
		@looping = false
	end

	def loopBlocking()
		lastTick = Time.now
		while @looping do
			#(calculate fps)
			tick = Time.now
			@fps =  1.0 / (tick - lastTick)
			lastTick = tick
			#(get events)
			while event = SDL::Event2.poll do
				onEvent(event)
			end
			#(render)
			render()
		end
	end

	#---------------
	# Object Methods
	#---------------
	
	def to_s
		"Window(#{@width}, #{@height})"
	end

end


## -----------------------------------------------------------------------------
## Application Entry Point
## -----------------------------------------------------------------------------

def hollowSquare
	Group.new(
		Polygon.fromPoints(
			[-1.5,-1.0,1.5],
			[1.5,-1.0,1.5],
			[1.5,1.0,1.5],
			[-1.5,1.0,1.5],
			nil).normal(0,0,1),
		Polygon.fromPoints(
			[1.5,-1.0,-1.5],
			[1.5,1.0,-1.5],
			[1.5,1.0,1.5],
			[1.5,-1.0,1.5],
			nil).normal(1,0,0),
		Polygon.fromPoints(
			[-1.5,-1.0,-1.5],
			[-1.5,1.0,-1.5],
			[1.5,1.0,-1.5],
			[1.5,-1.0,-1.5],
			nil).normal(0,0,-1),
		Polygon.fromPoints(
			[-1.5,-1.0,-1.5],
			[-1.5,-1.0,1.5],
			[-1.5,1.0,1.5],
			[-1.5,1.0,-1.5],
			nil).normal(-1,0,0),
	nil).color(1,1,1).rotate(0,0,0).texture('papersq.jpg')
end

if $0 == __FILE__
	Window.new(1024,1024).moveCameraAbs(0,1.5,8).addModule(
		Lighting.new.ambient(0.0).light(
			Point3D.new(0.0,25.0,0.0),
			Color.new(1.0,1.0,1.0),
			false)
		).addModule(GameKeys.new
		).addModule(InfoBar.new
		).draw(Polygon.rectangle(50,50).rotate(-90,0,0).offset(-25,0,25).texture('clothsq.jpg',25)
		).draw(Sphere.new.radius(25).texture('skysq.png')
		#).draw(Cube.new([0,0,0],[100,20,100]).offset(-50,0,-50)
		).draw(Cube.new.texture('cube.jpg')
		#).draw(hollowSquare
		).loopBlocking
end
