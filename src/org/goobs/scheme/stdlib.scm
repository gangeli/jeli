;;---------------------------
;;| Standard Scheme Library |
;;|         12/19/07        |
;;|       Gabor Angeli      |
;;---------------------------

;;pseudo-primitives
(define (list . lst)
  lst)
(define quit exit)

;;list helper functions
(define (caar x) (car (car x))) 
(define (cadr x) (car (cdr x))) 
(define (cddr x) (cdr (cdr x))) 
(define (cdar x) (cdr (car x))) 

(define (caaar x) (car (car (car x)))) 
(define (caadr x) (car (car (cdr x)))) 
(define (cadar x) (car (cdr (car x)))) 
(define (caddr x) (car (cdr (cdr x)))) 
(define (cdaar x) (cdr (car (car x)))) 
(define (cdadr x) (cdr (car (cdr x)))) 
(define (cddar x) (cdr (cdr (car x)))) 
(define (cdddr x) (cdr (cdr (cdr x)))) 

(define (caaaar x) (car (car (car (car x))))) 
(define (caaadr x) (car (car (car (cdr x))))) 
(define (caadar x) (car (car (cdr (car x))))) 
(define (caaddr x) (car (car (cdr (cdr x))))) 
(define (cadaar x) (car (cdr (car (car x))))) 
(define (cadadr x) (car (cdr (car (cdr x))))) 
(define (caddar x) (car (cdr (cdr (car x))))) 
(define (cadddr x) (car (cdr (cdr (cdr x))))) 
(define (cdaaar x) (cdr (car (car (car x))))) 
(define (cdaadr x) (cdr (car (car (cdr x))))) 
(define (cdadar x) (cdr (car (cdr (car x))))) 
(define (cdaddr x) (cdr (car (cdr (cdr x))))) 
(define (cddaar x) (cdr (cdr (car (car x))))) 
(define (cddadr x) (cdr (cdr (car (cdr x))))) 
(define (cdddar x) (cdr (cdr (cdr (car x))))) 
(define (cddddr x) (cdr (cdr (cdr (cdr x))))) 

(define (empty? lst) (eq? lst nil)) 
(define (length lst)
	(if (empty? lst)
		0
		(+ 1 (length (cdr lst)))))

;;math helper functions
(define (<= x y) (or (< x y) (= x y))) 
(define (>= x y) (or (> x y) (= x y))) 

;;table implementation
(define (assoc key lst)
  (cond ((null? lst) #f)
	((equal? (caar lst) key) (car lst))
	(else (assoc key (cdr lst))))) 

(define (lookup key table)
  (let ((record (assoc key (cdr table))))
    (if (not record)
        #f
        (cdr record)))) 

(define (insert! key value table)
  (let ((record (assoc key (cdr table))))
    (if (not record)
        (set-cdr! table
                  (cons (cons key value) (cdr table)))
        (set-cdr! record value))) 
  'ok) 

(define (make-table)
  (list '*table*)) 

;;list map and for-each
(define (map func lst)
  (if (empty? lst)
      nil
      (cons (func (car lst)) (map func (cdr lst))))) 

(define (for-each func lst)
  (if (empty? lst)
      nil
      (do (func (car lst))
	  (for-each func (cdr lst))))) 

;;standard predicates
(define (boolean? arg)
  (or (eq? arg #t)
      (eq? arg #f))) 

(define (null? x) 
  (equal? x nil)) 

(define (zero? x)
  (eq? x 0)) 


;;networking
(define net-conn cons)

