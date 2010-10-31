;;
; Spoof of the Arduino library
;       Gabor Angeli
;;

(define _pins (list "" "" "" "" "" "" "" "" "" "" "" "" ""))
(define (_set-pin pin str)
	(define (helper pin lst)
		(if (= pin 0)
			(set-car! lst str)
			(helper (- pin 1) (cdr lst))
		)
	)
	(helper pin _pins)
)
(define (_get-pin pin)
	(define (helper pin lst)
		(if (= pin 0)
			(car lst)
			(helper (- pin 1) (cdr lst))
		)
	)
	(helper pin _pins)
)

(define (add-listener)
	"ok"
)

(define (remove-listener)
	"ok"
)

(define (pin-high pin)
	(_set-pin pin "high")
	#t
)

(define (pin-low pin)
	(_set-pin pin "low")
	#t
)

(define (pwm pin freq)
	(_set-pin pin (string-append "pwm@" (number->string freq)))
	#t
)
(define (read-analog pin freq)
	500
)

(define (pins)
	(define (helper i)
		(if (>= i 13)
			"ok"
			(begin (print 
						(string-append "pin " 
										(number->string i) 
										":  " 
										(_get-pin i)
						)
					)
				(helper (+ i 1))
			)
		)
	)
	(helper 0)
)
